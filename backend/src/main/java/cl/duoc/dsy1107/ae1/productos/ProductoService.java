package cl.duoc.dsy1107.ae1.productos;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Las cinco operaciones del CRUD.
 *
 * Igual que IndicadoresService, esta capa no sabe nada de HTTP: recibe y
 * devuelve objetos del dominio, y cuando algo no cuadra lanza una excepcion
 * propia. Quien la convierte en codigo de estado es ManejadorDeErrores.
 */
@Service
public class ProductoService {

    /** Orden estable del listado. Ver la nota en ProductoRepository. */
    private static final Sort ORDEN = Sort.by(Sort.Direction.DESC, "creadoEn");

    private final ProductoRepository repositorio;

    /**
     * El reloj se inyecta en vez de llamar a Instant.now() dentro del metodo.
     * Es la misma decision que ya tomo CacheTtl: con el reloj como dependencia,
     * la prueba puede fijar el instante y comprobar creadoEn sin depender de
     * cuando corrio.
     */
    private final Clock reloj;

    public ProductoService(ProductoRepository repositorio, Clock reloj) {
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public List<Producto> listar() {
        return repositorio.findAll(ORDEN);
    }

    @Transactional(readOnly = true)
    public Producto obtener(long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new ProductoNoEncontradoException(id));
    }

    @Transactional
    public Producto crear(String nombre, int precio, int stock) {
        return repositorio.save(new Producto(nombre, precio, stock, reloj.instant()));
    }

    /**
     * Reemplaza los campos editables. creadoEn no se toca: es la fecha en que
     * el producto entro, no la del ultimo cambio, y por eso la columna esta
     * declarada updatable = false.
     *
     * Dentro de una transaccion, la entidad que devuelve findById esta
     * gestionada: los setters bastan y el UPDATE sale solo al cerrar. El save()
     * explicito seria redundante.
     */
    @Transactional
    public Producto actualizar(long id, String nombre, int precio, int stock) {
        Producto producto = obtener(id);
        producto.setNombre(nombre);
        producto.setPrecio(precio);
        producto.setStock(stock);
        return producto;
    }

    /**
     * Borrar algo que no existe es un 404 y no un 204 silencioso: quien llama
     * pidio borrar UN recurso concreto, y merece saber que no estaba.
     */
    @Transactional
    public void eliminar(long id) {
        if (!repositorio.existsById(id)) {
            throw new ProductoNoEncontradoException(id);
        }
        repositorio.deleteById(id);
    }
}
