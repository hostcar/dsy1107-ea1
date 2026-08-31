package cl.duoc.dsy1107.ae1;

import cl.duoc.dsy1107.ae1.config.BackendProperties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class Ae1ApplicationTests {

    @Autowired
    private BackendProperties props;

    @Test
    @DisplayName("el contexto levanta y application.yml queda bien enlazado")
    void contextLoads() {
        assertThat(props.mindicador().url()).isEqualTo("https://mindicador.cl/api");
        assertThat(props.mindicador().ttl()).isNotNull();
        assertThat(props.mindicador().disponibles()).contains("uf", "dolar", "utm");
        assertThat(props.cors().origenes()).contains("http://localhost:4200");
    }
}
