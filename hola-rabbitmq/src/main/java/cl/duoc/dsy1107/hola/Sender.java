package cl.duoc.dsy1107.hola;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Paso 5 de la guia: el productor.
 *
 * convertAndSend(routingKey, mensaje) publica en el EXCHANGE POR DEFECTO, el que
 * no tiene nombre (""). Ese exchange es de tipo direct y viene con una regla de
 * fabrica: cada cola queda enlazada a el con su propio nombre como routing key.
 * Por eso aqui la routing key "hello" funciona como si fuera "la direccion de la
 * cola". A partir de la 2.1.3 se publica en un exchange propio, y ahi la
 * routing key deja de ser el nombre de la cola.
 *
 * Una correccion a la guia: convertAndSend NO convierte a JSON. Con el
 * convertidor por defecto, un String viaja como texto plano (content_type
 * text/plain). Para JSON hay que registrar un JacksonJsonMessageConverter, como
 * hace el backend en su MensajeriaConfig.
 *
 * El productor no sabe si hay alguien escuchando. Si el consumidor esta apagado,
 * el envio igual resulta y el mensaje espera en la cola: eso es la
 * asincronia. Se ve en el panel, columna "Ready".
 */
@Component
public class Sender {

    private static final Logger log = LoggerFactory.getLogger(Sender.class);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final RabbitTemplate rabbitTemplate;

    public Sender(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Antepone la hora de envio, para poder compararla con la de recepcion y
     * ver cuanto tarda el viaje por el broker.
     *
     * Las excepciones no se atrapan aqui: si el broker no esta, quien llama
     * tiene que enterarse (el menu lo muestra, el controlador responde 503).
     */
    public String sendMessage(String message) {
        String fullMessage = "[" + LocalTime.now().format(HORA) + "] " + message;
        rabbitTemplate.convertAndSend(RabbitMQConfig.COLA, fullMessage);
        log.info("[✓] Mensaje enviado: '{}'", fullMessage);
        return fullMessage;
    }
}
