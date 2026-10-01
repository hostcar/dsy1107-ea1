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
public record BackendProperties(Mindicador mindicador, Cors cors, Mensajeria mensajeria) {

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

    /**
     * Los nombres de la topologia de RabbitMQ.
     *
     * ESTAN AQUI Y NO REPARTIDOS POR EL CODIGO a proposito. Un nombre de cola
     * escrito a mano dentro de un servicio es un contrato entre dos procesos
     * disfrazado de constante privada: el dia que cambie, el productor y el
     * consumidor dejan de encontrarse y no hay ni un error de compilacion que
     * lo avise. Los mensajes simplemente se quedan en una cola que nadie lee.
     *
     * Los MISMOS valores literales estan repetidos en el application.yml de
     * notificaciones-ms. No es un descuido ni algo que haya que "factorizar" a
     * una libreria compartida: son dos procesos que se despliegan por separado,
     * y el contrato que comparten es la topologia del broker, no una clase Java.
     * Compartir el jar los volveria a acoplar, que es justo lo que la cola vino
     * a deshacer.
     *
     * @param motor      "rabbit" (por defecto) o "sqs". Decide cual de las dos
     *                   implementaciones de PublicadorDeSolicitudes se crea.
     *                   Existe porque el Learner Lab NO tiene Amazon MQ: en la
     *                   sala se usa el RabbitMQ de docker-compose y en AWS, SQS.
     * @param exchange   el DirectExchange al que se publica. Solo con motor=rabbit.
     * @param routingKey la clave con la que se publica el evento. El exchange la
     *                   compara letra por letra con la del binding. Solo con
     *                   motor=rabbit.
     * @param colaUrl    la URL de la cola SQS (output sqs_cola_url de Terraform).
     *                   Solo con motor=sqs. En SQS no hay exchange ni routing
     *                   key: se publica directo a la cola, por su URL.
     */
    public record Mensajeria(String motor, String exchange, String routingKey, String colaUrl) {
    }
}
