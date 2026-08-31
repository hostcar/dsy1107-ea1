package cl.duoc.dsy1107.ae1.cache;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CacheTtlTest {

    private static final Instant INICIO = Instant.parse("2026-08-31T12:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(10);

    private RelojDePrueba reloj;
    private CacheTtl cache;

    @BeforeEach
    void preparar() {
        reloj = new RelojDePrueba(INICIO);
        cache = new CacheTtl(reloj, TTL);
    }

    @Test
    @DisplayName("lo que no se ha guardado no esta")
    void vacia() {
        assertThat(cache.buscarVigente("todos")).isEmpty();
        assertThat(cache.buscar("todos")).isEmpty();
    }

    @Test
    @DisplayName("dentro del TTL la entrada esta vigente y envejece")
    void vigente() {
        cache.guardar("todos", "{\"uf\":1}");

        reloj.avanzar(Duration.ofMinutes(4));

        CacheTtl.Entrada entrada = cache.buscarVigente("todos").orElseThrow();
        assertThat(entrada.cuerpo()).isEqualTo("{\"uf\":1}");
        assertThat(entrada.edadEn(reloj.instant())).isEqualTo(Duration.ofMinutes(4));
        assertThat(entrada.restanteEn(reloj.instant())).isEqualTo(Duration.ofMinutes(6));
    }

    @Test
    @DisplayName("pasado el TTL deja de estar vigente, pero NO se borra")
    void vencidaPeroPresente() {
        cache.guardar("todos", "{\"uf\":1}");

        reloj.avanzar(TTL.plusSeconds(1));

        // Esto es lo que habilita el stale-if-error: buscarVigente dice que no,
        // buscar dice que si.
        assertThat(cache.buscarVigente("todos")).isEmpty();
        assertThat(cache.buscar("todos")).isPresent();
        assertThat(cache.buscar("todos").orElseThrow().restanteEn(reloj.instant())).isZero();
    }

    @Test
    @DisplayName("guardar de nuevo reinicia el vencimiento")
    void reescribir() {
        cache.guardar("todos", "viejo");
        reloj.avanzar(TTL.plusSeconds(1));
        cache.guardar("todos", "nuevo");

        CacheTtl.Entrada entrada = cache.buscarVigente("todos").orElseThrow();
        assertThat(entrada.cuerpo()).isEqualTo("nuevo");
        assertThat(entrada.edadEn(reloj.instant())).isZero();
    }

    @Test
    @DisplayName("cada clave vence por su cuenta")
    void clavesIndependientes() {
        cache.guardar("uf", "a");
        reloj.avanzar(Duration.ofMinutes(6));
        cache.guardar("dolar", "b");
        reloj.avanzar(Duration.ofMinutes(6));

        assertThat(cache.buscarVigente("uf")).isEmpty();
        assertThat(cache.buscarVigente("dolar")).isPresent();
        assertThat(cache.todas()).hasSize(2);
    }

    @Test
    @DisplayName("vaciar devuelve cuantas entradas habia y deja la cache limpia")
    void vaciar() {
        cache.guardar("uf", "a");
        cache.guardar("dolar", "b");

        assertThat(cache.vaciar()).isEqualTo(2);
        assertThat(cache.todas()).isEmpty();
        assertThat(cache.vaciar()).isZero();
    }
}
