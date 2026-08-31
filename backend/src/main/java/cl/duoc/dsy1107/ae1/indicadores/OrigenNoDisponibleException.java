package cl.duoc.dsy1107.ae1.indicadores;

/**
 * El origen no respondio y no habia ninguna copia guardada, ni siquiera vencida,
 * con que responder. Es el unico caso en que el cliente recibe un 502.
 */
public class OrigenNoDisponibleException extends RuntimeException {

    private final String origen;

    public OrigenNoDisponibleException(String origen, String detalle, Throwable causa) {
        super(detalle, causa);
        this.origen = origen;
    }

    public String origen() {
        return origen;
    }
}
