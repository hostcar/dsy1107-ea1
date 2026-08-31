package cl.duoc.dsy1107.ae1.indicadores;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La capa HTTP: que cada situacion salga con el codigo y las cabeceras que
 * corresponden. El servicio esta simulado, asi que aqui no hay ni red ni cache.
 */
@SpringBootTest
@AutoConfigureMockMvc
class IndicadoresControllerTest {

    private static final String JSON = "{\"uf\":{\"valor\":39000}}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private IndicadoresService servicio;

    @Test
    @DisplayName("MISS: 200, X-Cache MISS y Cache-Control con el TTL completo")
    void miss() throws Exception {
        given(servicio.todos()).willReturn(
                new Respuesta(JSON, Respuesta.Origen.RED, Duration.ZERO, Duration.ofMinutes(10)));

        mvc.perform(get("/datos"))
                .andExpect(status().isOk())
                .andExpect(content().json(JSON))
                .andExpect(header().string("X-Cache", "MISS"))
                .andExpect(header().string("X-Cache-Edad", "0"))
                .andExpect(header().string("Cache-Control", "max-age=600, public"));
    }

    @Test
    @DisplayName("HIT: la edad viaja en la cabecera")
    void hit() throws Exception {
        given(servicio.todos()).willReturn(
                new Respuesta(JSON, Respuesta.Origen.CACHE, Duration.ofSeconds(42), Duration.ofSeconds(558)));

        mvc.perform(get("/datos"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cache", "HIT"))
                .andExpect(header().string("X-Cache-Edad", "42"));
    }

    @Test
    @DisplayName("STALE: se responde 200 pero se prohibe cachear aguas abajo")
    void stale() throws Exception {
        given(servicio.todos()).willReturn(
                new Respuesta(JSON, Respuesta.Origen.CACHE_VENCIDA, Duration.ofHours(2), Duration.ZERO));

        mvc.perform(get("/datos"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cache", "STALE"))
                .andExpect(header().string("Cache-Control", "no-cache"));
    }

    @Test
    @DisplayName("un indicador que no existe es 404 y dice cuales si existen")
    void desconocido() throws Exception {
        given(servicio.uno("pesos")).willThrow(
                new IndicadorDesconocidoException("pesos", List.of("uf", "dolar")));

        mvc.perform(get("/datos/pesos"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("No existe el indicador 'pesos'"))
                .andExpect(jsonPath("$.disponibles").isArray())
                .andExpect(jsonPath("$.disponibles[0]").value("uf"));
    }

    @Test
    @DisplayName("si el origen no responde y no hay cache es 502, no 500")
    void origenCaido() throws Exception {
        given(servicio.todos()).willThrow(
                new OrigenNoDisponibleException("https://mindicador.cl/api", "el origen respondio 503", null));

        mvc.perform(get("/datos"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("El origen https://mindicador.cl/api no esta disponible"))
                .andExpect(jsonPath("$.detalle").value("el origen respondio 503"));
    }

    @Test
    @DisplayName("GET /cache publica el estado de la cache")
    void estadoDeCache() throws Exception {
        given(servicio.estado()).willReturn(new Estado(
                "https://mindicador.cl/api", 600, 7, 1, 0,
                List.of(new Estado.EntradaEnCache("todos", 42, 558, true))));

        mvc.perform(get("/cache"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aciertos").value(7))
                .andExpect(jsonPath("$.llamadasARed").value(1))
                .andExpect(jsonPath("$.entradas[0].clave").value("todos"));
    }

    @Test
    @DisplayName("DELETE /cache informa cuantas entradas boto")
    void vaciarCache() throws Exception {
        given(servicio.vaciarCache()).willReturn(3);

        mvc.perform(delete("/cache"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entradasEliminadas").value(3));
    }
}
