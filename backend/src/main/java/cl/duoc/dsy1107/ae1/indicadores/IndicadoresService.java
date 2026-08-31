package cl.duoc.dsy1107.ae1.indicadores;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import cl.duoc.dsy1107.ae1.cache.CacheTtl;
import cl.duoc.dsy1107.ae1.config.BackendProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * El corazon del backend: pedir los indicadores al origen lo menos posible.
 *
 * La logica completa cabe en obtener():
 *
 *   1. Hay copia vigente  -> se entrega y no se toca la red.           (HIT)
 *   2. No hay             -> se llama al origen y se guarda.           (MISS)
 *   3. El origen fallo    -> si hay copia vencida, se entrega igual.   (STALE)
 *   4. Fallo y no hay nada guardado -> 502, que es la unica respuesta honesta.
 *
 * El paso 3 es el que separa un proxy de un proxy util. Un dia de clase con
 * mindicador.cl caido, sin ese paso, es una demo caida.
 *
 * Y es tambien la razon por la que NO hay un HealthIndicator de actuator que le
 * pegue a mindicador.cl: si el /actuator/health de este proceso se pusiera DOWN
 * cuando el origen se cae, Beanstalk declararia enferma —y terminaria
 * reemplazando— una instancia que esta respondiendo 200 con la copia en cache.
 * Un health check contesta "puedo atender", no "mi proveedor esta bien".
 */
@Service
public class IndicadoresService {

    private static final Logger log = LoggerFactory.getLogger(IndicadoresService.class);

    /** Clave con la que se guarda la respuesta completa (la de GET /datos). */
    static final String CLAVE_TODOS = "todos";

    private final RestClient cliente;
    private final CacheTtl cache;
    private final String urlOrigen;
    private final List<String> disponibles;

    private final AtomicLong aciertos = new AtomicLong();
    private final AtomicLong llamadasARed = new AtomicLong();
    private final AtomicLong respuestasVencidas = new AtomicLong();

    public IndicadoresService(RestClient clienteMindicador, CacheTtl cacheDeIndicadores, BackendProperties props) {
        this.cliente = clienteMindicador;
        this.cache = cacheDeIndicadores;
        this.urlOrigen = props.mindicador().url();
        this.disponibles = List.copyOf(props.mindicador().disponibles());
    }

    /** Todos los indicadores del dia: el equivalente exacto de GET https://mindicador.cl/api */
    public Respuesta todos() {
        return obtener(CLAVE_TODOS, URI.create(urlOrigen));
    }

    /**
     * Un solo indicador, con su serie historica.
     *
     * El nombre se valida contra la lista blanca ANTES de construir la URL. No
     * es paranoia: sin esa validacion, cualquier texto que llegue en la ruta se
     * reenvia al origen, y este backend pasa a ser un proxy abierto que alguien
     * mas puede usar para llamar a donde no corresponde.
     */
    public Respuesta uno(String indicador) {
        String nombre = indicador == null ? "" : indicador.trim().toLowerCase();
        if (!disponibles.contains(nombre)) {
            throw new IndicadorDesconocidoException(indicador, disponibles);
        }
        return obtener(nombre, URI.create(urlOrigen + "/" + nombre));
    }

    private Respuesta obtener(String clave, URI uri) {
        Instant ahora = cache.ahora();

        Optional<CacheTtl.Entrada> vigente = cache.buscarVigente(clave);
        if (vigente.isPresent()) {
            CacheTtl.Entrada entrada = vigente.get();
            aciertos.incrementAndGet();
            log.debug("HIT  {} (edad {} s)", clave, entrada.edadEn(ahora).toSeconds());
            return new Respuesta(entrada.cuerpo(), Respuesta.Origen.CACHE,
                    entrada.edadEn(ahora), entrada.restanteEn(ahora));
        }

        try {
            llamadasARed.incrementAndGet();
            log.debug("MISS {} -> {}", clave, uri);
            String cuerpo = cliente.get().uri(uri).retrieve().body(String.class);
            if (cuerpo == null || cuerpo.isBlank()) {
                throw new RestClientException("El origen respondio 200 con el cuerpo vacio");
            }
            CacheTtl.Entrada entrada = cache.guardar(clave, cuerpo);
            return new Respuesta(cuerpo, Respuesta.Origen.RED, Duration.ZERO, entrada.restanteEn(cache.ahora()));

        } catch (RestClientException e) {
            return anteElFallo(clave, uri, e);
        }
    }

    /** Paso 3 y paso 4: la copia vencida, o el 502. */
    private Respuesta anteElFallo(String clave, URI uri, RestClientException e) {
        String detalle = e instanceof RestClientResponseException http
                ? "el origen respondio " + http.getStatusCode().value()
                : e.getMessage();

        Optional<CacheTtl.Entrada> vencida = cache.buscar(clave);
        if (vencida.isPresent()) {
            CacheTtl.Entrada entrada = vencida.get();
            Instant ahora = cache.ahora();
            respuestasVencidas.incrementAndGet();
            log.warn("STALE {}: {}. Se entrega la copia de hace {} s.", clave, detalle, entrada.edadEn(ahora).toSeconds());
            return new Respuesta(entrada.cuerpo(), Respuesta.Origen.CACHE_VENCIDA,
                    entrada.edadEn(ahora), Duration.ZERO);
        }

        log.error("El origen {} fallo y no hay nada en cache: {}", uri, detalle);
        throw new OrigenNoDisponibleException(urlOrigen, detalle, e);
    }

    public Estado estado() {
        Instant ahora = cache.ahora();
        List<Estado.EntradaEnCache> entradas = cache.todas().stream()
                .map(e -> new Estado.EntradaEnCache(
                        e.clave(),
                        e.edadEn(ahora).toSeconds(),
                        e.restanteEn(ahora).toSeconds(),
                        e.vigenteEn(ahora)))
                .sorted((a, b) -> a.clave().compareTo(b.clave()))
                .toList();

        return new Estado(urlOrigen, cache.ttl().toSeconds(),
                aciertos.get(), llamadasARed.get(), respuestasVencidas.get(), entradas);
    }

    public int vaciarCache() {
        int habia = cache.vaciar();
        log.info("Cache vaciada a mano: {} entradas eliminadas", habia);
        return habia;
    }
}
