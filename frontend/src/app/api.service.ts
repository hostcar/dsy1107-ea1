import { HttpClient, HttpContext, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, firstValueFrom, map, of } from 'rxjs';

import { AuthService } from './auth.service';
import { ConfigService } from './config.service';
import { SIN_TOKEN } from './token.interceptor';

/** Respuesta uniforme para poder mostrar SIEMPRE el codigo HTTP en pantalla. */
export interface Resultado {
  readonly descripcion: string;
  readonly status: number;
  readonly ok: boolean;
  readonly cuerpo: unknown;
}

/**
 * Las tres llamadas del ejercicio. Todas usan el MISMO access token:
 *
 *   1. /oauth2/userInfo     -> la identidad, segun el estandar OIDC
 *   2. cognito-idp GetUser  -> la identidad, segun la API propia de AWS
 *   3. {apiUrl}/datos       -> mindicador.cl a traves del API Manager
 *
 * Que un solo token sirva para las tres es justamente lo que hace util a un
 * IDaaS: el permiso viaja en el token, no en cada backend.
 *
 * Ninguna de estas funciones escribe el header Authorization: lo pone el
 * interceptor. Lo que si deciden es cuando NO enviarlo, con el contexto
 * SIN_TOKEN.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly cfg = inject(ConfigService);
  private readonly auth = inject(AuthService);

  /** 1. Endpoint /userInfo de OIDC (lamina 14 de la presentacion 1.2.1). */
  userInfo(): Promise<Resultado> {
    return this.ejecutar(
      'GET /oauth2/userInfo (OIDC)',
      this.http.get(`${this.cfg.valor.cognitoDomain}/oauth2/userInfo`, { observe: 'response' }),
    );
  }

  /**
   * 2. La API de usuario propia de Cognito: GetUser.
   *
   * No es REST: la operacion viaja en el header X-Amz-Target y el token en el
   * cuerpo, no en Authorization. Por eso el interceptor la ignora (su URL no es
   * ni el API Gateway ni el dominio del Hosted UI). Requiere el scope
   * aws.cognito.signin.user.admin.
   */
  getUser(): Promise<Resultado> {
    return this.ejecutar(
      'POST cognito-idp GetUser (API de AWS)',
      this.http.post(
        `https://cognito-idp.${this.cfg.valor.region}.amazonaws.com/`,
        { AccessToken: this.auth.accessToken() },
        {
          observe: 'response',
          headers: {
            'Content-Type': 'application/x-amz-json-1.1',
            'X-Amz-Target': 'AWSCognitoIdentityProviderService.GetUser',
          },
        },
      ),
    );
  }

  /**
   * 3. La API de la actividad 1.1.2, ahora detras del authorizer.
   *
   * @param conToken si es false se omite el header a proposito, para provocar
   *                 el 401. Los dos botones llaman al MISMO endpoint: la unica
   *                 diferencia es esa cabecera.
   */
  indicadores(conToken: boolean): Promise<Resultado> {
    return this.ejecutar(
      conToken ? 'GET /datos con token' : 'GET /datos SIN token',
      this.http.get(`${this.cfg.valor.apiUrl}/datos`, {
        observe: 'response',
        context: new HttpContext().set(SIN_TOKEN, !conToken),
      }),
    );
  }

  /** La ruta gemela sin authorizer, para comparar. */
  indicadoresPublicos(): Promise<Resultado> {
    return this.ejecutar(
      'GET /publico/datos (ruta sin proteger)',
      this.http.get(`${this.cfg.valor.apiUrl}/publico/datos`, {
        observe: 'response',
        context: new HttpContext().set(SIN_TOKEN, true),
      }),
    );
  }

  private ejecutar(
    descripcion: string,
    peticion: Observable<HttpResponse<unknown>>,
  ): Promise<Resultado> {
    return firstValueFrom(
      peticion.pipe(
        map((respuesta) => ({
          descripcion,
          status: respuesta.status,
          ok: true,
          cuerpo: respuesta.body,
        })),
        catchError((error: HttpErrorResponse) =>
          of({
            descripcion,
            status: error.status,
            ok: false,
            // status 0 casi siempre es CORS: el navegador ni siquiera deja leer
            // la respuesta. Revisa cors_configuration en apigateway.tf.
            cuerpo:
              error.status === 0
                ? `Error de red o CORS: ${error.message}`
                : (error.error ?? error.statusText),
          }),
        ),
      ),
    );
  }
}
