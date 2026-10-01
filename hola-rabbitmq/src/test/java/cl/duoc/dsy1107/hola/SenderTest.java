package cl.duoc.dsy1107.hola;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SenderTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final Sender sender = new Sender(rabbitTemplate);

    @Test
    void publicaEnElExchangePorDefectoConElNombreDeLaColaComoRoutingKey() {
        String enviado = sender.sendMessage("Hola");

        // La sobrecarga de dos argumentos es (routingKey, mensaje): exchange "".
        verify(rabbitTemplate).convertAndSend(eq("hello"), eq(enviado));
    }

    @Test
    void anteponeLaHoraDeEnvio() {
        assertThat(sender.sendMessage("Hola")).matches("\\[\\d{2}:\\d{2}:\\d{2}\\.\\d{3}] Hola");
    }

    @Test
    void noSeTragaElErrorDelBroker() {
        doThrow(new AmqpConnectException(new ConnectException()))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString());

        assertThatThrownBy(() -> sender.sendMessage("Hola"))
                .isInstanceOf(AmqpException.class);
    }
}
