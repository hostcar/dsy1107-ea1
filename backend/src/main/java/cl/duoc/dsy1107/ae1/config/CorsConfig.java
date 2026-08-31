package cl.duoc.dsy1107.ae1.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS para el modo local (actividad 1.1.4).
 *
 * Cuando el front pasa por el API Gateway, el CORS lo resuelve alla el
 * cors_configuration de apigateway.tf y estas reglas ni se consultan: el
 * navegador nunca habla con este servicio. Esto existe para poder apuntar
 * "ng serve" directamente al :8080 y comparar las dos rutas.
 *
 * Ojo con el detalle que se repite en toda la EA1: el origen va SIN barra
 * final, porque un header Origin nunca la lleva.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final BackendProperties props;

    public CorsConfig(BackendProperties props) {
        this.props = props;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(props.cors().origenes().toArray(String[]::new))
                .allowedMethods("GET", "DELETE", "OPTIONS")
                .allowedHeaders("authorization", "content-type")
                // Sin esto el JavaScript del front puede recibir la respuesta pero
                // no leer X-Cache: los headers no estandar no se exponen solos.
                .exposedHeaders("X-Cache", "X-Cache-Edad")
                .maxAge(300);
    }
}
