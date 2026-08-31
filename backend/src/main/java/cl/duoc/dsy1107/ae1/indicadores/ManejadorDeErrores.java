package cl.duoc.dsy1107.ae1.indicadores;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce las excepciones del servicio a codigos HTTP.
 *
 * Que el error salga en JSON y con un codigo correcto no es cosmetica: el front
 * de la actividad muestra SIEMPRE el status (ver Resultado en api.service.ts).
 * Un 500 generico con una pagina de error de Spring no le dice nada al alumno;
 * un 502 con "el origen respondio 503" le dice exactamente quien fallo.
 */
@RestControllerAdvice
public class ManejadorDeErrores {

    /**
     * @param error   que paso, en una linea
     * @param detalle el porque, cuando se sabe
     * @param momento cuando, para poder cruzarlo con los logs
     */
    public record ErrorHttp(String error, String detalle, Instant momento) {
    }

    /** Indicador que no existe: 404, y de paso se dice cuales si existen. */
    @ExceptionHandler(IndicadorDesconocidoException.class)
    public ResponseEntity<Cuerpo404> desconocido(IndicadorDesconocidoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new Cuerpo404(e.getMessage(), e.disponibles(), Instant.now()));
    }

    public record Cuerpo404(String error, List<String> disponibles, Instant momento) {
    }

    /**
     * El origen no responde y no hay copia guardada.
     *
     * 502 y no 500: el que fallo no fue este servicio, fue aquel del que depende.
     * La diferencia importa cuando alguien tiene que decidir a quien despertar.
     */
    @ExceptionHandler(OrigenNoDisponibleException.class)
    public ResponseEntity<ErrorHttp> origenCaido(OrigenNoDisponibleException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorHttp("El origen " + e.origen() + " no esta disponible",
                        e.getMessage(), Instant.now()));
    }
}
