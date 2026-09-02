package cl.duoc.dsy1107.ae1.productos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Una fila de la tabla "producto".
 *
 * Esta clase NO sale por HTTP. Lo que viaja al cliente son los records de
 * ProductoController, y la conversion se hace a mano. Publicar la entidad
 * directo es comodo durante cinco minutos y despues cuesta caro: cualquier
 * cambio del esquema se convierte en un cambio de la API publica sin que nadie
 * lo haya decidido, y al reves, un campo interno que se agregue a la tabla
 * aparece solo en las respuestas.
 *
 * Es una clase y no un record porque JPA necesita constructor sin argumentos y
 * campos mutables para poder hidratar el objeto.
 */
@Entity
@Table(name = "producto")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    /**
     * En pesos chilenos, entero.
     *
     * No es un double: la aritmetica binaria de punto flotante no representa
     * exacto ni 0,1, y el dinero es justo donde ese error se nota. El peso no
     * tiene decimales, asi que un entero es la representacion correcta, no un
     * atajo. Si algun dia hubiera monedas con centavos, lo que corresponde es
     * BigDecimal, nunca double.
     */
    @Column(nullable = false)
    private int precio;

    @Column(nullable = false)
    private int stock;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    /** Exigido por JPA. No usarlo desde el codigo. */
    protected Producto() {
    }

    public Producto(String nombre, int precio, int stock, Instant creadoEn) {
        this.nombre = nombre;
        this.precio = precio;
        this.stock = stock;
        this.creadoEn = creadoEn;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public int getPrecio() {
        return precio;
    }

    public void setPrecio(int precio) {
        this.precio = precio;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
