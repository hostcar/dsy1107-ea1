package cl.duoc.dsy1107.hola;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Paso 4 de la guia: la cola.
 *
 * Declarar un @Bean de tipo Queue no crea nada en ese momento. Spring Boot
 * registra un RabbitAdmin que, la primera vez que se abre una conexion con el
 * broker, recorre todos los beans Queue y los declara. Declarar es idempotente:
 * si la cola no existe se crea, si ya existe con los mismos argumentos se
 * reutiliza.
 *
 * Lo que NO se puede es redeclararla con argumentos distintos. Si cambias
 * durable con la cola ya creada, el arranque falla con
 * PRECONDITION_FAILED - inequivalent arg 'durable'. Se arregla borrando la cola
 * desde el panel (Queues and Streams > hello > Delete) y volviendo a arrancar.
 */
@Configuration
public class RabbitMQConfig {

    /** El nombre lo comparten el productor y el consumidor. */
    public static final String COLA = "hello";

    /**
     * durable=TRUE, no false como en la guia.
     *
     * La guia declara new Queue("hello", false) -- una cola no durable -- y dice
     * que "se borra si RabbitMQ se reinicia". Con RabbitMQ 4 eso ya no arranca:
     * las colas clasicas no durables y no exclusivas estan deprecadas
     * (transient_nonexcl_queues), y desde la 4.3 el broker las RECHAZA. El
     * sintoma no menciona la palabra durable:
     *
     *   INTERNAL_ERROR - Feature `transient_nonexcl_queues` is deprecated.
     *   ... NOT_FOUND - no queue 'hello' in vhost '/'
     *   Application run failed
     *
     * durable=true solo dice que la DEFINICION de la cola sobrevive a un
     * reinicio. Que los mensajes sobrevivan es otra cosa (mensajes persistentes,
     * actividad 2.2.1).
     */
    @Bean
    Queue helloQueue() {
        return new Queue(COLA, true);
    }
}
