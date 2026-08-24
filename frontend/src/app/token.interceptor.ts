import { HttpContextToken, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { AuthService } from './auth.service';
import { ConfigService } from './config.service';

/**
 * Marca una peticion para que salga SIN el header Authorization.
 *
 *     this.http.get(url, { context: new HttpContext().set(SIN_TOKEN, true) })
 *
 * Existe por dos motivos didacticos: el boton "/datos sin token", que provoca
 * el 401 a proposito, y la ruta publica, que no necesita credencial.
 */
export const SIN_TOKEN = new HttpContextToken<boolean>(() => false);

/**
 * Agrega "Authorization: Bearer <access_token>" a las peticiones que van al
 * API Gateway o al propio Cognito.
 *
 * Es el patron de la actividad 1.3.2 (alli con MSAL, aqui a mano): el token se
 * adjunta en un solo lugar, y solo a los destinos que confiamos. Enviarlo a
 * cualquier URL seria una fuga de credenciales.
 *
 * Se envia el ACCESS token, no el ID token: el primero dice que puedes hacer y
 * es el que valida el authorizer; el segundo solo dice quien eres.
 */
export const tokenInterceptor: HttpInterceptorFn = (req, next) => {
  const cfg = inject(ConfigService).actual();
  const token = inject(AuthService).accessToken();

  if (!cfg || !token || req.context.get(SIN_TOKEN)) {
    return next(req);
  }

  const destinoConfiable = req.url.startsWith(cfg.apiUrl) || req.url.startsWith(cfg.cognitoDomain);

  if (!destinoConfiable) {
    return next(req);
  }

  return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};
