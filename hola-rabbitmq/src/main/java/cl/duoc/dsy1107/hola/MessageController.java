package cl.duoc.dsy1107.hola;

import org.springframework.amqp.AmqpException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Paso 9 de la guia (opcional): enviar por HTTP en vez de por el menu.
 *
 *   curl -X POST http://localhost:8090/api/messages \
 *        -H "Content-Type: application/json" -d '{"message":"Hola desde REST"}'
 *
 *   curl "http://localhost:8090/api/messages/send?message=HolaMundo"
 *
 * Fijate en lo que responde: 202 Accepted y no 200 OK. El controlador no sabe si
 * el mensaje se proceso, solo que el broker lo recibio. Es la diferencia entre
 * una llamada sincrona y una asincrona, dicha con un codigo HTTP.
 *
 * Si el broker esta caido responde 503, no 400 como en la guia: el cliente no
 * hizo nada mal.
 */
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final Sender sender;

    public MessageController(Sender sender) {
        this.sender = sender;
    }

    public record MessageRequest(String message) {}

    @PostMapping
    public ResponseEntity<String> sendMessage(@RequestBody MessageRequest request) {
        return enviar(request.message());
    }

    /** Mas comodo para probar desde el navegador; en una API real, solo POST. */
    @GetMapping("/send")
    public ResponseEntity<String> sendMessageGet(@RequestParam String message) {
        return enviar(message);
    }

    private ResponseEntity<String> enviar(String message) {
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body("Falta el mensaje");
        }
        try {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body("Mensaje enviado: " + sender.sendMessage(message));
        } catch (AmqpException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("RabbitMQ no disponible: " + e.getMessage());
        }
    }
}
