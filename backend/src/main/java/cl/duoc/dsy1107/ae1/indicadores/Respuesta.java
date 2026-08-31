package cl.duoc.dsy1107.ae1.indicadores;

import java.time.Duration;

/**
 * Lo que devuelve el servicio: el JSON tal cual vino del origen, mas la
 * trazabilidad de DE DONDE salio. Esa segunda parte es la que termina en las
 * cabeceras X-Cache y hace visible en clase algo que normalmente es invisible.
 *
 * @param cuerpo   el JSON del origen, sin tocar. Este backend no lo reinterpreta:
 *                 un proxy que reserializa introduce diferencias que nadie pidio.
 * @param origen   red, cache vigente o cache vencida.
 * @param edad     cuanto lleva guardado el dato que se entrega. Cero si vino de la red.
 * @param restante cuanto le queda de vida util. Alimenta el Cache-Control.
 */
public record Respuesta(String cuerpo, Origen origen, Duration edad, Duration restante) {

    public enum Origen {

        /** Hubo que salir a la red. */
        RED("MISS"),

        /** Estaba guardado y todavia vigente. */
        CACHE("HIT"),

        /** El origen fallo y se entrego la copia vencida (stale-if-error). */
        CACHE_VENCIDA("STALE");

        private final String cabecera;

        Origen(String cabecera) {
            this.cabecera = cabecera;
        }

        /** El valor que viaja en X-Cache, con los nombres que usa cualquier CDN. */
        public String cabecera() {
            return cabecera;
        }
    }
}
