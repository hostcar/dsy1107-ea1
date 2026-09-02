package cl.duoc.dsy1107.ae1.productos;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * El acceso a la tabla.
 *
 * No lleva ni una linea de codigo: Spring Data implementa la interfaz en
 * tiempo de arranque a partir de su nombre y su firma. findAll, findById, save
 * y deleteById vienen de JpaRepository.
 *
 * El Sort de findAll(Sort) se usa desde el servicio para que el listado tenga
 * un orden estable. Sin ORDER BY, PostgreSQL no promete ninguno, y un listado
 * que cambia de orden entre dos llamadas identicas es de esos errores que solo
 * aparecen en la demostracion.
 */
public interface ProductoRepository extends JpaRepository<Producto, Long> {
}
