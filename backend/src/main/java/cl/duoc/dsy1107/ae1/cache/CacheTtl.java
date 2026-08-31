package cl.duoc.dsy1107.ae1.cache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Una cache en memoria con vencimiento por tiempo. Escrita a mano y a
 * proposito: son cuarenta lineas, se leen enteras, y dejan ver que un TTL no es
 * mas que "guarde cuando" comparado contra "que hora es".
 *
 * Dos detalles que si importan:
 *
 *   1. El reloj se inyecta (java.time.Clock). Sin eso, probar el vencimiento
 *      obligaria a dormir el test; con esto, el test adelanta la hora.
 *   2. Una entrada vencida NO se borra. Se marca vencida y sigue disponible,
 *      porque servir un dato viejo suele ser mejor que servir un error cuando
 *      el origen no responde (ver buscar()).
 *
 * En un sistema real esto seria Caffeine o Redis. La idea es la misma.
 */
public class CacheTtl {

    /** Una respuesta guardada, con su fecha de nacimiento y su fecha de vencimiento. */
    public record Entrada(String clave, String cuerpo, Instant guardadoEn, Instant expiraEn) {

        public boolean vigenteEn(Instant ahora) {
            return ahora.isBefore(expiraEn);
        }

        public Duration edadEn(Instant ahora) {
            return Duration.between(guardadoEn, ahora);
        }

        /** Cuanto le queda de vida. Nunca negativo: se usa para el Cache-Control. */
        public Duration restanteEn(Instant ahora) {
            Duration restante = Duration.between(ahora, expiraEn);
            return restante.isNegative() ? Duration.ZERO : restante;
        }
    }

    private final Map<String, Entrada> entradas = new ConcurrentHashMap<>();
    private final Clock reloj;
    private final Duration ttl;

    public CacheTtl(Clock reloj, Duration ttl) {
        this.reloj = reloj;
        this.ttl = ttl;
    }

    /** La entrada solo si todavia sirve. Es el camino feliz: acierto de cache. */
    public Optional<Entrada> buscarVigente(String clave) {
        return buscar(clave).filter(entrada -> entrada.vigenteEn(ahora()));
    }

    /**
     * La entrada aunque este vencida. Se usa cuando el origen fallo: mejor un
     * dato de hace media hora, y decirlo en la cabecera, que un 502.
     */
    public Optional<Entrada> buscar(String clave) {
        return Optional.ofNullable(entradas.get(clave));
    }

    public Entrada guardar(String clave, String cuerpo) {
        Instant ahora = ahora();
        Entrada entrada = new Entrada(clave, cuerpo, ahora, ahora.plus(ttl));
        entradas.put(clave, entrada);
        return entrada;
    }

    /** Vacia la cache y devuelve cuantas entradas habia. Lo usa DELETE /cache. */
    public int vaciar() {
        int habia = entradas.size();
        entradas.clear();
        return habia;
    }

    public List<Entrada> todas() {
        return List.copyOf(entradas.values());
    }

    public Instant ahora() {
        return reloj.instant();
    }

    public Duration ttl() {
        return ttl;
    }
}
