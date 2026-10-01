package cl.duoc.dsy1107.hola;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Paso 6 de la guia: el consumidor.
 *
 * UN SOLO @RabbitListener, a diferencia de la guia. La guia pone dos metodos
 * escuchando la misma cola "hello" y los presenta como dos formas de leer el
 * mismo mensaje. No es asi: son dos consumidores que COMPITEN. RabbitMQ reparte
 * los mensajes por turnos (round-robin), y cada uno lo recibe solo uno de los
 * dos: la mitad de los mensajes aparece con un formato y la otra mitad con el
 * otro. Es el patron punto a punto de la clase 2.1.1 -- util, pero es otra
 * cosa. Para verlo a proposito, levanta dos copias de esta aplicacion (ver
 * README).
 *
 * SOBRE EL ACK. Con la configuracion por defecto (acknowledge-mode AUTO de
 * Spring) el contenedor confirma el mensaje cuando este metodo TERMINA BIEN, no
 * cuando el mensaje llega. Si el metodo lanza una excepcion, el mensaje se
 * devuelve a la cola.
 *
 * Ojo con eso ultimo: sin mas configuracion, un mensaje que siempre falla vuelve
 * a la cola, se entrega de nuevo, falla otra vez, y asi para siempre. La guia
 * dice que "Spring AMQP reintentara segun la configuracion de retry", pero el
 * retry que configura es el del PRODUCTOR (spring.rabbitmq.template.retry), no
 * el del consumidor. La salida correcta a ese bucle es la DLQ de la 2.2.2.
 */
@Component
public class Receiver {

    private static final Logger log = LoggerFactory.getLogger(Receiver.class);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /**
     * El id no lo usa la aplicacion: es el nombre con que la prueba e2e
     * (EnvioYRecepcionIT) encuentra este listener para detenerlo, arrancarlo y
     * ver que recibio.
     */
    @RabbitListener(id = RabbitMQConfig.COLA, queues = RabbitMQConfig.COLA)
    public void receiveMessage(String message) {
        log.info("[{}] [✓] Mensaje recibido: '{}'", LocalTime.now().format(HORA), message);
        // Aqui iria la logica de procesamiento del mensaje.
    }
}
