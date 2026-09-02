package cl.duoc.dsy1107.ae1.productos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El CRUD completo, de la peticion HTTP hasta la base.
 *
 * A diferencia de IndicadoresControllerTest, aqui NO se simula el servicio: la
 * peticion recorre controlador, servicio, repositorio y H2 de verdad. Eso es lo
 * que hace que estas pruebas comprueben tambien la migracion -- si
 * V1__producto.sql y la entidad dejaran de coincidir, el contexto ni siquiera
 * levantaria, porque ddl-auto esta en validate.
 *
 * @Transactional revierte cada prueba al terminar. Sin eso, el orden en que
 * JUnit las ejecute cambiaria lo que ve el listado, y la suite fallaria un dia
 * sin que nadie haya tocado nada.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductoControllerTest {

    private static final String TECLADO = """
            {"nombre":"Teclado mecanico","precio":49990,"stock":7}""";

    @Autowired
    private MockMvc mvc;

    private long crearTeclado() throws Exception {
        String respuesta = mvc.perform(post("/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TECLADO))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return Long.parseLong(respuesta.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    @DisplayName("POST crea, responde 201 y dice en Location donde quedo")
    void crear() throws Exception {
        mvc.perform(post("/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TECLADO))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nombre").value("Teclado mecanico"))
                .andExpect(jsonPath("$.precio").value(49990))
                .andExpect(jsonPath("$.stock").value(7))
                // Lo pone el servidor con su reloj, no el cliente: el record de
                // entrada ni siquiera tiene el campo.
                .andExpect(jsonPath("$.creadoEn").exists());
    }

    @Test
    @DisplayName("GET /productos lista lo que se creo")
    void listar() throws Exception {
        crearTeclado();

        mvc.perform(get("/productos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Teclado mecanico"));
    }

    @Test
    @DisplayName("GET /productos/{id} devuelve uno")
    void obtener() throws Exception {
        long id = crearTeclado();

        mvc.perform(get("/productos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.stock").value(7));
    }

    @Test
    @DisplayName("PUT reemplaza los campos editables")
    void actualizar() throws Exception {
        long id = crearTeclado();

        mvc.perform(put("/productos/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Teclado inalambrico","precio":39990,"stock":2}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.nombre").value("Teclado inalambrico"))
                .andExpect(jsonPath("$.precio").value(39990));
    }

    @Test
    @DisplayName("DELETE borra y responde 204 sin cuerpo")
    void eliminar() throws Exception {
        long id = crearTeclado();

        mvc.perform(delete("/productos/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/productos/" + id)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("un id que no existe es 404, en GET, PUT y DELETE")
    void noEncontrado() throws Exception {
        mvc.perform(get("/productos/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Producto no encontrado"));

        mvc.perform(put("/productos/9999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TECLADO))
                .andExpect(status().isNotFound());

        // Borrar lo que no esta NO es un 204 silencioso: quien llamo pidio
        // borrar un recurso concreto y merece saber que no estaba.
        mvc.perform(delete("/productos/9999")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("un cuerpo invalido es 400 y dice que campo fallo")
    void invalido() throws Exception {
        mvc.perform(post("/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"  ","precio":-1,"stock":3}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nombre").exists())
                .andExpect(jsonPath("$.campos.precio").value("el precio no puede ser negativo"));
    }
}
