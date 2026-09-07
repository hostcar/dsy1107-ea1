/**
 * user-token-ms -- el puente entre "a que grupo pertenece el usuario" y
 * "que dice el claim scope de su access token".
 *
 * Cognito lo invoca en el trigger Pre Token Generation V2: despues de haber
 * autenticado al usuario y justo antes de firmar los tokens. Es el unico punto
 * del sistema donde el permiso deja de ser una propiedad del USUARIO y pasa a
 * ser una propiedad del TOKEN, que es lo unico que el API Gateway sabe leer.
 *
 * Sin este Lambda no existe autorizacion por usuario en Cognito: los scopes de
 * un user pool se declaran en el app client (allowed_oauth_scopes) y son
 * identicos para todos los que entren por el.
 *
 * Se dispara en CADA emision de token, incluido el refresh. Por eso quitarle un
 * grupo a alguien surte efecto en cuanto expire su access token, sin necesidad
 * de revocar nada.
 */

/**
 * El unico lugar del sistema donde se decide quien puede que.
 *
 * Un grupo desconocido no rompe nada: aporta cero scopes. Es deliberado --
 * agregar un grupo en la consola de Cognito no debe poder conceder permisos
 * que este archivo no contemple.
 */
const SCOPES_POR_GRUPO = {
  lectores: ['productos/read'],
  editores: ['productos/read', 'productos/write'],
};

export const handler = async (event) => {
  // Cognito ya resolvio la pertenencia a grupos antes de llamarnos: no hay que
  // consultar el directorio, viene resuelta en el evento.
  const grupos = event.request.groupConfiguration?.groupsToOverride ?? [];

  // El Set evita que alguien que este en los dos grupos reciba productos/read
  // dos veces en el mismo claim.
  const scopes = [...new Set(grupos.flatMap((grupo) => SCOPES_POR_GRUPO[grupo] ?? []))];

  // Queda en CloudWatch. En clase es la prueba de que el Lambda corrio y de por
  // que el token salio como salio.
  //
  // Se registra el correo y no event.userName: cuando el pool usa el correo como
  // nombre de usuario (username_attributes = ["email"]), userName trae el sub,
  // un UUID que en una demo no le dice nada a nadie.
  console.log(
    JSON.stringify({
      usuario: event.request.userAttributes?.email ?? event.userName,
      grupos,
      scopes,
    }),
  );

  event.response = {
    claimsAndScopeOverrideDetails: {
      // Solo el access token, y esto es lo importante de toda la actividad:
      // el ID token dice QUIEN eres, el access token dice QUE PUEDES HACER.
      // Poner scope en el ID token es el error conceptual que 1.2.1 evita.
      accessTokenGeneration: {
        scopesToAdd: scopes,
      },
    },
  };

  return event;
};
