package cl.duoc.dsy1107.ae1.cache;

import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Un reloj que se mueve a mano. Es la razon por la que CacheTtl recibe un Clock
 * en vez de llamar a Instant.now(): probar un TTL de 10 minutos sin esto
 * significaria dormir el test 10 minutos.
 */
public class RelojDePrueba extends Clock {

    private Instant instante;

    public RelojDePrueba(Instant inicio) {
        this.instante = inicio;
    }

    public void avanzar(Duration cuanto) {
        instante = instante.plus(cuanto);
    }

    @Override
    public Instant instant() {
        return instante;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zona) {
        return this;
    }
}
