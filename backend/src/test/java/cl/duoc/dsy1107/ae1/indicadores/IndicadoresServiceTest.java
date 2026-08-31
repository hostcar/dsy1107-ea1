package cl.duoc.dsy1107.ae1.indicadores;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import cl.duoc.dsy1107.ae1.cache.CacheTtl;
import cl.duoc.dsy1107.ae1.cache.RelojDePrueba;
import cl.duoc.dsy1107.ae1.config.BackendProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Las pruebas que importan de este backend son las de la cache: sin ellas, que
 * la segunda peticion no salga a la red es un acto de fe.
 *
 * No se levanta Spring ni se llama a mindicador.cl. MockRestServiceServer se
 * mete dentro del RestClient y responde por el; ademas cuenta las llamadas, que
 * es justo lo que hay que verificar.
 */
class IndicadoresServiceTest {

    private static final String URL = "https://mindicador.cl/api";
    private static final String JSON = "{\"uf\":{\"valor\":39000}}";
    private static final Duration TTL = Duration.ofMinutes(10);

    private RelojDePrueba reloj;
    private MockRestServiceServer origen;
    private IndicadoresService servicio;

    @BeforeEach
    void preparar() {
        reloj = new RelojDePrueba(Instant.parse("2026-08-31T12:00:00Z"));

        RestClient.Builder builder = RestClient.builder();
        origen = MockRestServiceServer.bindTo(builder).build();

        BackendProperties props = new BackendProperties(
                new BackendProperties.Mindicador(URL, TTL, Duration.ofSeconds(3), Duration.ofSeconds(10),
                        List.of("uf", "dolar", "utm")),
                new BackendProperties.Cors(List.of("http://localhost:4200")));

        servicio = new IndicadoresService(builder.build(), new CacheTtl(reloj, TTL), props);
    }

    @Test
    @DisplayName("la primera peticion sale a la red y responde MISS")
    void primeraLlamada() {
        origen.expect(requestTo(URL)).andRespond(withSuccess(JSON, org.springframework.http.MediaType.APPLICATION_JSON));

        Respuesta respuesta = servicio.todos();

        assertThat(respuesta.cuerpo()).isEqualTo(JSON);
        assertThat(respuesta.origen()).isEqualTo(Respuesta.Origen.RED);
        assertThat(respuesta.edad()).isZero();
        assertThat(respuesta.restante()).isEqualTo(TTL);
        origen.verify();
    }

    @Test
    @DisplayName("la segunda peticion NO toca la red: responde HIT desde la cache")
    void segundaLlamadaDesdeCache() {
        // once() es la asercion real de este test: si el servicio saliera dos
        // veces a la red, MockRestServiceServer falla.
        origen.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withSuccess(JSON, org.springframework.http.MediaType.APPLICATION_JSON));

        servicio.todos();
        reloj.avanzar(Duration.ofMinutes(3));
        Respuesta segunda = servicio.todos();

        assertThat(segunda.origen()).isEqualTo(Respuesta.Origen.CACHE);
        assertThat(segunda.cuerpo()).isEqualTo(JSON);
        assertThat(segunda.edad()).isEqualTo(Duration.ofMinutes(3));
        assertThat(segunda.restante()).isEqualTo(Duration.ofMinutes(7));
        origen.verify();
    }

    @Test
    @DisplayName("vencido el TTL vuelve a salir a la red")
    void venceElTtl() {
        origen.expect(ExpectedCount.twice(), requestTo(URL))
                .andRespond(withSuccess(JSON, org.springframework.http.MediaType.APPLICATION_JSON));

        servicio.todos();
        reloj.avanzar(TTL.plusSeconds(1));
        Respuesta segunda = servicio.todos();

        assertThat(segunda.origen()).isEqualTo(Respuesta.Origen.RED);
        origen.verify();
    }

    @Test
    @DisplayName("si el origen se cae y hay copia vencida, se entrega la copia (STALE)")
    void staleSiElOrigenFalla() {
        origen.expect(requestTo(URL)).andRespond(withSuccess(JSON, org.springframework.http.MediaType.APPLICATION_JSON));
        origen.expect(requestTo(URL)).andRespond(withServerError());

        servicio.todos();
        reloj.avanzar(TTL.plusSeconds(1));
        Respuesta segunda = servicio.todos();

        assertThat(segunda.origen()).isEqualTo(Respuesta.Origen.CACHE_VENCIDA);
        assertThat(segunda.cuerpo()).isEqualTo(JSON);
        assertThat(segunda.restante()).isZero();
        origen.verify();
    }

    @Test
    @DisplayName("si el origen se cae y no hay nada guardado, 502")
    void sinCacheNiOrigen() {
        origen.expect(requestTo(URL)).andRespond(withException(new IOException("connection reset")));

        assertThatThrownBy(() -> servicio.todos())
                .isInstanceOf(OrigenNoDisponibleException.class);
        origen.verify();
    }

    @Test
    @DisplayName("un indicador de la lista se pide en su propia URL y se cachea aparte")
    void indicadorPropio() {
        origen.expect(ExpectedCount.once(), requestTo(URL + "/uf"))
                .andRespond(withSuccess(JSON, org.springframework.http.MediaType.APPLICATION_JSON));

        assertThat(servicio.uno("uf").origen()).isEqualTo(Respuesta.Origen.RED);
        assertThat(servicio.uno("UF ").origen()).isEqualTo(Respuesta.Origen.CACHE);
        origen.verify();
    }

    @Test
    @DisplayName("un indicador fuera de la lista blanca no llega a la red")
    void indicadorDesconocido() {
        assertThatThrownBy(() -> servicio.uno("../../etc/passwd"))
                .isInstanceOf(IndicadorDesconocidoException.class);

        // Sin expectativas declaradas, cualquier llamada saliente habria fallado.
        origen.verify();
    }

    @Test
    @DisplayName("el estado cuenta aciertos y llamadas a la red")
    void estado() {
        origen.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withSuccess(JSON, org.springframework.http.MediaType.APPLICATION_JSON));

        servicio.todos();
        servicio.todos();
        servicio.todos();

        Estado estado = servicio.estado();
        assertThat(estado.llamadasARed()).isEqualTo(1);
        assertThat(estado.aciertos()).isEqualTo(2);
        assertThat(estado.origen()).isEqualTo(URL);
        assertThat(estado.ttlSegundos()).isEqualTo(600);
        assertThat(estado.entradas()).singleElement()
                .satisfies(e -> assertThat(e.vigente()).isTrue());
    }
}
