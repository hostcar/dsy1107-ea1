/**
 * Leer un JWT por dentro (actividades 1.2.7 y 1.2.8).
 *
 * Aqui NO se verifica la firma: eso lo hace el API Gateway con las claves
 * publicas de /jwks. Este codigo solo decodifica base64url para mostrar los
 * claims en pantalla, igual que el comando de la guia 1.2.2b:
 *
 *     echo $TOKEN | cut -d. -f2 | base64 -d | python3 -m json.tool
 *
 * Que cualquiera pueda leerlo es la mitad de la leccion: el JWT no es secreto
 * porque este cifrado, sino porque esta firmado.
 */

export interface Claims {
  readonly [clave: string]: unknown;
  /** Expiracion, en segundos Unix. */
  readonly exp?: number;
  readonly iss?: string;
  readonly aud?: string;
  readonly client_id?: string;
  readonly scope?: string;
  readonly email?: string;
  readonly name?: string;
}

export function decodificarJwt(token: string | null | undefined): Claims | null {
  if (!token) return null;

  try {
    // Un JWT son tres partes: encabezado . contenido . firma. La del medio es
    // la unica que interesa mostrar.
    const payload = token.split('.')[1];
    if (!payload) return null;

    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    const bytes = Uint8Array.from(atob(base64), (caracter) => caracter.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes)) as Claims;
  } catch {
    return null;
  }
}
