package cl.duoc.dsy1107.ae1.productos;

/**
 * El id pedido no esta en la tabla. Lo traduce a 404 el ManejadorDeErrores.
 *
 * Excepcion propia y no ResponseStatusException por el mismo motivo que en el
 * paquete indicadores: el servicio no deberia saber que existe HTTP. Decide que
 * el producto no esta; que eso sea un 404 es decision de la capa web.
 */
public class ProductoNoEncontradoException extends RuntimeException {

    private final long id;

    public ProductoNoEncontradoException(long id) {
        super("No existe un producto con id " + id);
        this.id = id;
    }

    public long id() {
        return id;
    }
}
