package cl.duoc.dsy1107.ae1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * DSY1107 - EA1 - El backend que queda DETRAS del API Manager.
 *
 * En la actividad 1.1.2 el API Gateway integraba directamente contra
 * https://mindicador.cl/api. Este servicio ocupa ese lugar: expone /datos con
 * la misma forma de respuesta, pero agrega lo que un API publica de terceros no
 * te puede dar:
 *
 *   - una cache con TTL, para no golpear el origen en cada peticion de la clase
 *   - una respuesta util cuando el origen se cae (stale-if-error)
 *   - cabeceras X-Cache que dejan ver, en vivo, cuando se uso la red
 *
 * Lo que este backend NO hace, a proposito: no valida el token. De eso se
 * encarga el JWT authorizer del API Gateway (apigateway.tf). Aqui se ve el
 * modelo del API Manager en su forma pura: la seguridad vive en el borde y el
 * servicio de atras se dedica a su negocio.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class Ae1Application {

    public static void main(String[] args) {
        SpringApplication.run(Ae1Application.class, args);
    }
}
