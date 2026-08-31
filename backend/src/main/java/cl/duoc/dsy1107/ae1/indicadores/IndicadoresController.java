package cl.duoc.dsy1107.ae1.indicadores;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * La cara publica del backend.
 *
 * La ruta /datos no es casual: es la misma que expone el API Gateway en
 * apigateway.tf y la misma que llama api.service.ts en el front. Asi, cambiar
 * la variable backend_url de terraform para que apunte aqui en vez de a
 * mindicador.cl es un cambio de una linea, y ni el front ni el authorizer se
 * enteran.
 *
 * Ninguno de estos metodos mira el token: para cuando la peticion llega hasta
 * aqui, el JWT authorizer ya dijo que si.
 */
@RestController
public class IndicadoresController {

    private final IndicadoresService servicio;

    public IndicadoresController(IndicadoresService servicio) {
        this.servicio = servicio;
    }

    /** Todos los indicadores del dia. Es la ruta que consume el front. */
    @GetMapping("/datos")
    public ResponseEntity<String> datos() {
        return responder(servicio.todos());
    }

    /** Un indicador con su serie: /datos/uf, /datos/dolar, /datos/utm... */
    @GetMapping("/datos/{indicador}")
    public ResponseEntity<String> datosDe(@PathVariable String indicador) {
        return responder(servicio.uno(indicador));
    }

    /**
     * Estado de la cache: aciertos, llamadas a la red y que hay guardado.
     *
     * No se llama /salud a proposito. La salud del proceso la responde
     * /actuator/health, que es lo que sondea Beanstalk (lamina 16 de la guia
     * 1.3.8); esto de aca es otra cosa: cuanta red se esta ahorrando. Con
     * DELETE forman el par natural sobre el mismo recurso.
     */
    @GetMapping("/cache")
    public Estado cache() {
        return servicio.estado();
    }

    /**
     * Vacia la cache para forzar una llamada al origen.
     *
     * Existe para la clase: se muestra un HIT, se vacia, y el mismo GET vuelve
     * a decir MISS. En un sistema real esta ruta iria protegida (o no existiria);
     * aqui solo bota copias de datos publicos.
     */
    @DeleteMapping("/cache")
    public CacheVaciada vaciarCache() {
        return new CacheVaciada(servicio.vaciarCache());
    }

    public record CacheVaciada(int entradasEliminadas) {
    }

    /**
     * Arma la respuesta HTTP a partir de lo que devolvio el servicio.
     *
     * Las cabeceras X-Cache son la parte didactica: dejan ver, peticion a
     * peticion, si hubo red o no. Es el mismo vocabulario (MISS/HIT/STALE) que
     * usa cualquier CDN, asi que no es un invento local.
     */
    private ResponseEntity<String> responder(Respuesta respuesta) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Cache", respuesta.origen().cabecera())
                .header("X-Cache-Edad", String.valueOf(respuesta.edad().toSeconds()));

        // Una copia vencida no se puede seguir cacheando aguas abajo: el
        // navegador la mostraria como si fuera fresca.
        CacheControl control = respuesta.origen() == Respuesta.Origen.CACHE_VENCIDA
                ? CacheControl.noCache()
                : CacheControl.maxAge(respuesta.restante().toSeconds(), TimeUnit.SECONDS).cachePublic();

        return builder.cacheControl(control).body(respuesta.cuerpo());
    }
}
