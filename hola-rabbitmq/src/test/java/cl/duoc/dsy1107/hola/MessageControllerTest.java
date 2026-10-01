package cl.duoc.dsy1107.hola;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.http.HttpStatus;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MessageControllerTest {

    private final Sender sender = mock(Sender.class);
    private final MessageController controller = new MessageController(sender);

    @Test
    void respondeAcceptedPorqueNoSabeSiElMensajeYaSeProceso() {
        when(sender.sendMessage("Hola")).thenReturn("[10:00:00.000] Hola");

        var respuesta = controller.sendMessage(new MessageController.MessageRequest("Hola"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(respuesta.getBody()).contains("[10:00:00.000] Hola");
    }

    @Test
    void brokerCaidoEs503NoUnErrorDelCliente() {
        when(sender.sendMessage(anyString())).thenThrow(new AmqpConnectException(new ConnectException("refused")));

        assertThat(controller.sendMessageGet("Hola").getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void mensajeVacioNiSiquieraSeEnvia() {
        assertThat(controller.sendMessage(new MessageController.MessageRequest("  ")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(sender);
    }
}
