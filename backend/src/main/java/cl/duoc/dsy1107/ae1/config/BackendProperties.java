package cl.duoc.dsy1107.ae1.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Todo lo configurable del backend, en un solo lugar y sin valores quemados en
 * el codigo. Los defaults viven en application.yml; cualquiera se puede pisar
 * con una variable de entorno, que es como se configurara el dia que esto
 * corra en un contenedor:
 *
 *   BACKEND_MINDICADOR_TTL=1m java -jar ae1.jar
 */
@ConfigurationProperties(prefix = "backend")
public record BackendProperties(Mindicador mindicador, Cors cors) {

    /**
     * @param url            el origen que se consume. Es exactamente el mismo valor
     *                       que la variable backend_url de terraform.
     * @param ttl            cuanto vale una respuesta guardada antes de volver a la red.
     *                       Los indicadores cambian una vez al dia: 10 minutos es
     *                       generoso y aun asi evita cientos de llamadas en una clase.
     * @param conectarEn     tope para abrir la conexion con el origen.
     * @param leerEn         tope para que el origen termine de responder.
     * @param disponibles    lista blanca de indicadores. Sirve para dos cosas: responder
     *                       404 sin salir a la red, y no reenviar al origen cualquier
     *                       texto que llegue en la URL.
     */
    public record Mindicador(
            String url,
            Duration ttl,
            Duration conectarEn,
            Duration leerEn,
            List<String> disponibles) {
    }

    /**
     * Origenes que pueden llamar a este backend desde un navegador.
     *
     * En el despliegue real el CORS lo resuelve el API Gateway (actividad 1.1.4)
     * y el navegador nunca habla con este servicio. Esto existe para el modo
     * local: "ng serve" en :4200 apuntando derecho al :8080, sin AWS de por
     * medio.
     */
    public record Cors(List<String> origenes) {
    }
}
