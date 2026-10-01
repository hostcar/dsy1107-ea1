package cl.duoc.dsy1107.hola;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.fail;

/**
 * SOLO EL PRODUCTOR. Envia mensajes a la cola hello y no escucha nada, para que
 * sea OTRO proceso -- la aplicacion levantada aparte -- el que los reciba.
 *
 *   Terminal 1:  ./mvnw spring-boot:run                  (el consumidor)
 *   Terminal 2:  ./mvnw test -Dtest=SoloEnvioE2E          (el productor)
 *
 * En la terminal 1 van apareciendo los "Mensaje recibido", uno por segundo. Es
 * el Hello World con productor y consumidor por fin en procesos distintos, que
 * es como se usa una cola de verdad.
 *
 * Se ajusta desde la linea de comandos:
 *
 *   -Denvio.cantidad=20       cuantos mensajes (10 por defecto)
 *   -Denvio.intervalo=200     milisegundos entre uno y otro (1000 por defecto)
 *
 * Experimentos que salen solos:
 *   - Sin la aplicacion levantada, los mensajes quedan en la cola (panel:
 *     Ready = N). Al levantarla, le llegan todos de golpe.
 *   - Con DOS copias de la aplicacion (la segunda con --server.port=8091),
 *     cada mensaje lo recibe una sola: se los reparten por turnos.
 *
 * POR QUE NO SE LLAMA ...Test NI ...IT: para que no corra sola. Con ese nombre
 * la tomarian "./mvnw verify" (y la CI) o el perfil e2e, y sus mensajes
 * ensuciarian la cola de las otras pruebas. Solo corre cuando se la nombra con
 * -Dtest.
 *
 * El listener va apagado aqui (auto-startup=false): si esta prueba tuviera su
 * propio Receiver, competiria con el de la aplicacion y se quedaria con parte de
 * los mensajes. Y sin servidor web (NONE), para no chocar con el 8090 de la
 * aplicacion que esta corriendo al lado.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.rabbitmq.listener.simple.auto-startup=false",
                // Confirmaciones del broker: cada envio espera el "recibido"
                // de RabbitMQ. Sin esto, la prueba solo sabria que el mensaje
                // SALIO, no que LLEGO.
                "spring.rabbitmq.publisher-confirm-type=simple"
        })
class SoloEnvioE2E {

    @Autowired
    Sender sender;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    RabbitAdmin admin;

    @Test
    @DisplayName("envia mensajes a la cola hello para que los reciba la aplicacion levantada aparte")
    void enviar() throws InterruptedException {
        int cantidad = Integer.getInteger("envio.cantidad", 10);
        long intervalo = Long.getLong("envio.intervalo", 1000L);

        QueueInformation antes = infoDeLaCola();
        System.out.printf("%n>>> Enviando %d mensajes a 'hello', uno cada %d ms. Consumidores conectados: %d%n%n",
                cantidad, intervalo, antes.getConsumerCount());

        for (int i = 1; i <= cantidad; i++) {
            String mensaje = "prueba-envio #" + i + " de " + cantidad;
            // invoke() fija un canal para todo lo que corre dentro, y ahi se
            // espera la confirmacion. Si el broker no confirma en 5 s, falla.
            rabbitTemplate.invoke(canal -> {
                sender.sendMessage(mensaje);
                canal.waitForConfirmsOrDie(5_000);
                return null;
            });
            if (i < cantidad) {
                Thread.sleep(intervalo);
            }
        }

        int consumidores = infoDeLaCola().getConsumerCount();
        if (consumidores == 0) {
            System.out.printf("%n>>> RabbitMQ confirmo los %d mensajes, pero NADIE escucha 'hello': quedaron esperando "
                    + "en la cola. Levanta la aplicacion (./mvnw spring-boot:run) y le llegaran.%n%n", cantidad);
        } else {
            System.out.printf("%n>>> RabbitMQ confirmo los %d mensajes. Revisa la consola de la aplicacion "
                    + "(%d consumidor(es) conectados).%n%n", cantidad, consumidores);
        }
    }

    /**
     * La primera consulta abre la conexion, y con ella el RabbitAdmin declara la
     * cola hello si no existia (el bean de RabbitMQConfig). Por eso nunca es null.
     */
    private QueueInformation infoDeLaCola() {
        try {
            return admin.getQueueInfo(RabbitMQConfig.COLA);
        } catch (AmqpException e) {
            return fail("""
                    No hay RabbitMQ en localhost:5672 (%s).
                    Levantalo desde la raiz del repositorio:
                        docker compose up -d --wait rabbitmq""".formatted(e.getMessage()));
        }
    }
}
