package cl.duoc.dsy1107.hola;

import org.springframework.amqp.AmqpException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import java.util.Scanner;

/**
 * Paso 7 de la guia: la aplicacion, con su menu en la consola.
 *
 * Productor y consumidor viven en el mismo proceso para que el Hello World se
 * vea con una sola terminal. En un sistema real serian procesos distintos
 * (como el backend y notificaciones-ms en este mismo repositorio), y justo eso
 * es lo que gana la cola: que el que envia no dependa del que recibe.
 */
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        ApplicationContext ctx = SpringApplication.run(Application.class, args);
        Sender sender = ctx.getBean(Sender.class);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("RabbitMQ - Hello World con Spring Boot");
        System.out.println("=".repeat(60));
        System.out.println("Panel: http://localhost:15672  (guest / guest)");
        System.out.println("REST:  http://localhost:8090/api/messages/send?message=Hola");

        menu(sender);
    }

    /**
     * Si no hay consola (la entrada estandar se cierra, por ejemplo al correr
     * en segundo plano), el menu termina pero la aplicacion NO: sigue
     * escuchando la cola y atendiendo el endpoint REST.
     */
    private static void menu(Sender sender) {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\n--- Menú ---");
            System.out.println("1. Enviar mensaje");
            System.out.println("2. Enviar múltiples mensajes");
            System.out.println("3. Salir");
            System.out.print("Selecciona opción (1-3): ");

            if (!scanner.hasNextLine()) {
                System.out.println("\n[i] Sin consola: el menú se cierra, la aplicación sigue escuchando.");
                return;
            }
            try {
                switch (scanner.nextLine().trim()) {
                    case "1" -> {
                        System.out.print("Escribe el mensaje: ");
                        if (scanner.hasNextLine()) {
                            sender.sendMessage(scanner.nextLine());
                        }
                    }
                    case "2" -> {
                        System.out.print("¿Cuántos mensajes? ");
                        int count = Integer.parseInt(scanner.nextLine().trim());
                        for (int i = 1; i <= count; i++) {
                            sender.sendMessage("Mensaje #" + i + " - Hello RabbitMQ!");
                            Thread.sleep(500);
                        }
                    }
                    case "3" -> {
                        System.out.println("[✓] Saliendo...");
                        System.exit(0);
                    }
                    default -> System.out.println("[✗] Opción no válida");
                }
            } catch (NumberFormatException e) {
                System.out.println("[✗] Número inválido");
            } catch (AmqpException e) {
                // El menu sobrevive a un broker caido: se levanta RabbitMQ y se
                // vuelve a intentar sin reiniciar la aplicacion.
                System.out.println("[✗] No se pudo enviar: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
