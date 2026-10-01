package cl.duoc.dsy1107.hola;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Levanta el contexto sin RabbitMQ: la conexion es perezosa y el perfil
 * "pruebas" apaga el listener. El menu de Application.main no corre aqui.
 */
@SpringBootTest
class ApplicationTests {

    @Autowired
    Queue helloQueue;

    /**
     * durable=true no es gusto: RabbitMQ 4.3 rechaza las colas no durables y
     * no exclusivas (ver RabbitMQConfig). Si alguien vuelve al false de la
     * guia, esta prueba lo dice antes que el broker.
     */
    @Test
    void laColaEsHelloYDurable() {
        assertThat(helloQueue.getName()).isEqualTo("hello");
        assertThat(helloQueue.isDurable()).isTrue();
    }
}
