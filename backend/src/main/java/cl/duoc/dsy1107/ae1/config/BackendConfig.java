package cl.duoc.dsy1107.ae1.config;

import java.net.http.HttpClient;
import java.time.Clock;

import cl.duoc.dsy1107.ae1.cache.CacheTtl;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Las tres piezas que el resto de la aplicacion recibe ya armadas: el cliente
 * HTTP hacia el origen, el reloj y la cache.
 */
@Configuration
public class BackendConfig {

    /**
     * El cliente con el que se llama a mindicador.cl.
     *
     * Los dos timeouts no son decoracion. Un backend sin timeout de lectura
     * hereda la lentitud del servicio del que depende: si el origen se queda
     * colgado, este servicio se queda colgado con el, y despues el API Gateway
     * (que corta a los 29 s), y despues el navegador. Cortar temprano y devolver
     * la copia de la cache es justamente lo que hace IndicadoresService.
     */
    @Bean
    RestClient clienteMindicador(RestClient.Builder builder, BackendProperties props) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(props.mindicador().conectarEn())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(props.mindicador().leerEn());

        return builder.requestFactory(factory).build();
    }

    /**
     * El reloj como bean, y no Instant.now() repartido por el codigo: es lo que
     * permite que CacheTtlTest adelante el tiempo en vez de dormir el test.
     */
    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }

    @Bean
    CacheTtl cacheDeIndicadores(Clock reloj, BackendProperties props) {
        return new CacheTtl(reloj, props.mindicador().ttl());
    }
}
