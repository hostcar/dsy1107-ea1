package cl.duoc.dsy1107.ae1.indicadores;

import java.util.List;

/** El indicador pedido no esta en la lista blanca: 404 sin salir a la red. */
public class IndicadorDesconocidoException extends RuntimeException {

    private final String indicador;
    private final List<String> disponibles;

    public IndicadorDesconocidoException(String indicador, List<String> disponibles) {
        super("No existe el indicador '" + indicador + "'");
        this.indicador = indicador;
        this.disponibles = List.copyOf(disponibles);
    }

    public String indicador() {
        return indicador;
    }

    public List<String> disponibles() {
        return disponibles;
    }
}
