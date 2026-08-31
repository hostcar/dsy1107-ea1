package cl.duoc.dsy1107.ae1.indicadores;

import java.util.List;

/**
 * Lo que responde GET /cache: cuanta red se esta ahorrando y que hay guardado.
 *
 * Ojo con no confundirlo con /actuator/health, que responde si el proceso esta
 * vivo y es lo que sondea Beanstalk. Son preguntas distintas: este backend
 * puede estar perfectamente sano (UP) y a la vez sirviendo copias vencidas
 * porque mindicador.cl se cayo.
 *
 * @param origen        a quien se le estan pidiendo los datos
 * @param ttlSegundos   cuanto dura una respuesta guardada
 * @param aciertos      peticiones resueltas sin tocar la red
 * @param llamadasARed  peticiones que si salieron al origen
 * @param respuestasVencidas veces que el origen fallo y se sirvio la copia vieja
 * @param entradas      que hay guardado ahora mismo
 */
public record Estado(
        String origen,
        long ttlSegundos,
        long aciertos,
        long llamadasARed,
        long respuestasVencidas,
        List<EntradaEnCache> entradas) {

    public record EntradaEnCache(String clave, long edadSegundos, long restanteSegundos, boolean vigente) {
    }
}
