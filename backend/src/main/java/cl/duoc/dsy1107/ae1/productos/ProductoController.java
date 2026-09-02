package cl.duoc.dsy1107.ae1.productos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * El CRUD de /productos.
 *
 * Comparte proceso con IndicadoresController a proposito: es el mismo
 * despliegue, la misma task de ECS y la misma imagen. Separarlo en un servicio
 * aparte solo para tener dos cosas distintas agregaria un cluster, un registro
 * y una integracion mas al ejercicio sin ensenar nada nuevo.
 *
 * Tampoco mira el token, igual que el resto del backend: quien decide si la
 * peticion entra es el JWT authorizer del API Gateway. Ver la nota de
 * IndicadoresController y el README.
 *
 * OJO: estas rutas NO pasan por la integracion de /datos. Esa tiene el metodo y
 * la URI fijos (siempre GET .../datos), asi que un POST que entrara por ahi
 * llegaria aqui convertido en GET. Las rutas /productos tienen sus propias
 * integraciones en terraform/apigateway.tf.
 */
@RestController
@RequestMapping("/productos")
public class ProductoController {

    private final ProductoService servicio;

    public ProductoController(ProductoService servicio) {
        this.servicio = servicio;
    }

    /**
     * Lo que se acepta al crear o modificar.
     *
     * Es un record aparte de la entidad, y no por ceremonia: aqui no hay ni id
     * ni creadoEn. Si el cliente pudiera mandarlos, podria elegir su propio id
     * o falsear la fecha de creacion. Lo que no esta en el record no se puede
     * escribir, y esa es toda la validacion que hace falta para ese riesgo.
     */
    public record ProductoNuevo(
            @NotBlank(message = "el nombre es obligatorio")
            @Size(max = 120, message = "el nombre no puede pasar de 120 caracteres")
            String nombre,

            @PositiveOrZero(message = "el precio no puede ser negativo")
            int precio,

            @PositiveOrZero(message = "el stock no puede ser negativo")
            int stock) {
    }

    /** Lo que se devuelve. */
    public record ProductoVista(Long id, String nombre, int precio, int stock, Instant creadoEn) {

        static ProductoVista de(Producto p) {
            return new ProductoVista(p.getId(), p.getNombre(), p.getPrecio(), p.getStock(), p.getCreadoEn());
        }
    }

    @GetMapping
    public List<ProductoVista> listar() {
        return servicio.listar().stream().map(ProductoVista::de).toList();
    }

    @GetMapping("/{id}")
    public ProductoVista obtener(@PathVariable long id) {
        return ProductoVista.de(servicio.obtener(id));
    }

    /**
     * 201 con Location, no 200.
     *
     * Es lo que distingue un POST que crea de uno que solo procesa: la cabecera
     * dice donde quedo el recurso nuevo, y el cliente no tiene que adivinar la
     * URL a partir del id.
     */
    @PostMapping
    public ResponseEntity<ProductoVista> crear(@Valid @RequestBody ProductoNuevo datos) {
        Producto creado = servicio.crear(datos.nombre(), datos.precio(), datos.stock());
        ProductoVista vista = ProductoVista.de(creado);
        return ResponseEntity.created(URI.create("/productos/" + vista.id())).body(vista);
    }

    @PutMapping("/{id}")
    public ProductoVista actualizar(@PathVariable long id, @Valid @RequestBody ProductoNuevo datos) {
        return ProductoVista.de(servicio.actualizar(id, datos.nombre(), datos.precio(), datos.stock()));
    }

    /** 204: se borro y no hay nada que devolver. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable long id) {
        servicio.eliminar(id);
    }
}
