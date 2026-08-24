import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';

import { ConfigService } from './config.service';
import { Claims, decodificarJwt } from './jwt';

/** Lo que devuelve /oauth2/token. */
export interface Tokens {
  readonly access_token: string;
  readonly id_token: string;
  readonly refresh_token?: string;
  readonly expires_in: number;
  readonly token_type: string;
}

/**
 * openid es obligatorio en OIDC (lamina 13 de la presentacion 1.2.1) y es
 * ademas el scope que exige el authorizer del API Gateway.
 * El ultimo habilita la API GetUser de Cognito.
 */
const SCOPES = 'openid email profile aws.cognito.signin.user.admin';

const CLAVE_TOKENS = 'dsy1107.tokens';
const CLAVE_VERIFIER = 'dsy1107.pkce_verifier';
const CLAVE_STATE = 'dsy1107.state';

/**
 * Authorization Code + PKCE (RFC 7636) contra Cognito, escrito a mano.
 *
 * Se hace sin Amplify ni angular-oauth2-oidc a proposito: son unas 100 lineas y
 * en ellas se ve el flujo completo de la presentacion 1.2.1. En un proyecto
 * real se usa una libreria; para aprender el protocolo, no.
 *
 *   1. login()            genera code_verifier, lo guarda y redirige a /authorize
 *   2. Cognito            muestra el Hosted UI y vuelve a redirectUri con ?code=
 *   3. procesarRetorno()  cambia ese code por tokens en /token, enviando el
 *                         code_verifier. Sin el, /token responde 400.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly cfg = inject(ConfigService);

  /** Tokens de la sesion, o null. Es la unica fuente de verdad de la pantalla. */
  readonly tokens = signal<Tokens | null>(this.leerSesion());

  /** Ultimo error del flujo (state que no calza, /token que falla, etc.). */
  readonly error = signal<string | null>(null);

  /**
   * Reloj de un segundo. Existe para que la expiracion del token se vea en
   * vivo: sin el, la pantalla seguiria diciendo "sesion activa" hasta que
   * alguien hiciera clic.
   */
  private readonly ahora = signal(Date.now());

  readonly idClaims = computed<Claims | null>(() => decodificarJwt(this.tokens()?.id_token));
  readonly accessClaims = computed<Claims | null>(() =>
    decodificarJwt(this.tokens()?.access_token),
  );

  readonly segundosRestantes = computed(() => {
    const exp = this.accessClaims()?.exp;
    if (!exp) return 0;
    return Math.max(0, Math.floor((exp * 1000 - this.ahora()) / 1000));
  });

  readonly sesionActiva = computed(() => this.tokens() !== null && this.segundosRestantes() > 0);

  constructor() {
    const reloj = setInterval(() => this.ahora.set(Date.now()), 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(reloj));
  }

  accessToken(): string | null {
    return this.tokens()?.access_token ?? null;
  }

  // ---------------------------------------------------------------------------
  // Paso 1: mandar al usuario al servidor de autorizacion
  // ---------------------------------------------------------------------------

  async login(): Promise<void> {
    const { cognitoDomain, clientId, redirectUri } = this.cfg.valor;

    const verifier = aleatorioBase64Url(32);
    const challenge = await calcularChallenge(verifier);
    const state = aleatorioBase64Url(16);

    sessionStorage.setItem(CLAVE_VERIFIER, verifier);
    sessionStorage.setItem(CLAVE_STATE, state);

    const params = new URLSearchParams({
      response_type: 'code',
      client_id: clientId,
      redirect_uri: redirectUri,
      scope: SCOPES,
      state,
      // Viaja la huella (SHA-256) del secreto, nunca el secreto.
      code_challenge: challenge,
      code_challenge_method: 'S256',
    });

    // A partir de aqui la contrasena la escribe el usuario en Cognito.
    // Nuestro front nunca la ve: ese es el punto de OAuth2.
    window.location.assign(`${cognitoDomain}/oauth2/authorize?${params}`);
  }

  // ---------------------------------------------------------------------------
  // Paso 3: cambiar el code por tokens
  // ---------------------------------------------------------------------------

  /** La llama el componente al cargar: si la URL trae ?code=, lo canjea. */
  async procesarRetorno(): Promise<void> {
    if (!this.cfg.listo()) return;

    const url = new URL(window.location.href);
    const code = url.searchParams.get('code');
    const errorOauth = url.searchParams.get('error');

    if (errorOauth) {
      this.limpiarUrl();
      this.error.set(`${errorOauth}: ${url.searchParams.get('error_description') ?? ''}`);
      return;
    }
    if (!code) return;

    // Si el state no calza, la respuesta no corresponde a la peticion que
    // iniciamos: es la defensa contra CSRF del propio flujo.
    if (url.searchParams.get('state') !== sessionStorage.getItem(CLAVE_STATE)) {
      this.limpiarUrl();
      this.error.set('El parametro state no coincide. Se descarta la respuesta.');
      return;
    }

    const verifier = sessionStorage.getItem(CLAVE_VERIFIER);
    if (!verifier) {
      this.limpiarUrl();
      this.error.set('No hay code_verifier en esta pestana. Vuelve a iniciar sesion.');
      return;
    }

    const { cognitoDomain, clientId, redirectUri } = this.cfg.valor;

    // Se usa fetch y no HttpClient a proposito: esta llamada NO lleva el header
    // Authorization (todavia no hay token) y asi queda claro que /token se
    // autentica con el code + el code_verifier, nada mas.
    const respuesta = await fetch(`${cognitoDomain}/oauth2/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'authorization_code',
        client_id: clientId,
        code,
        redirect_uri: redirectUri,
        code_verifier: verifier,
      }),
    });

    const datos: unknown = await respuesta.json().catch(() => null);

    this.limpiarUrl();
    sessionStorage.removeItem(CLAVE_VERIFIER);
    sessionStorage.removeItem(CLAVE_STATE);

    if (!respuesta.ok) {
      const detalle = (datos as { error?: string } | null)?.error ?? 'error desconocido';
      this.error.set(`/oauth2/token respondio ${respuesta.status}: ${detalle}`);
      return;
    }

    this.guardarSesion(datos as Tokens);
  }

  // ---------------------------------------------------------------------------
  // Cierre de sesion
  // ---------------------------------------------------------------------------

  logout(): void {
    sessionStorage.removeItem(CLAVE_TOKENS);
    this.tokens.set(null);

    const { cognitoDomain, clientId, redirectUri } = this.cfg.valor;

    // Borrar el token local no cierra la sesion en el IDaaS: si no pasamos por
    // /logout, el proximo login entra solo. Eso es SSO (presentacion 1.2.2), y
    // es lo mismo que se observa con prompt=none en la guia 1.2.2b.
    const params = new URLSearchParams({ client_id: clientId, logout_uri: redirectUri });
    window.location.assign(`${cognitoDomain}/logout?${params}`);
  }

  // ---------------------------------------------------------------------------
  // Almacenamiento
  //
  // sessionStorage: se borra al cerrar la pestana y no se comparte entre
  // origenes. Sigue siendo accesible desde JavaScript, asi que un XSS puede
  // leer el token. Por eso los tokens duran 60 minutos y existe la revocacion.
  // ---------------------------------------------------------------------------

  private leerSesion(): Tokens | null {
    const crudo = sessionStorage.getItem(CLAVE_TOKENS);
    if (!crudo) return null;
    try {
      return JSON.parse(crudo) as Tokens;
    } catch {
      return null;
    }
  }

  private guardarSesion(tokens: Tokens): void {
    sessionStorage.setItem(CLAVE_TOKENS, JSON.stringify(tokens));
    this.tokens.set(tokens);
    this.error.set(null);
  }

  /** Saca el ?code= de la barra de direcciones: ya se uso y es de un solo uso. */
  private limpiarUrl(): void {
    window.history.replaceState({}, document.title, window.location.pathname);
  }
}

// -----------------------------------------------------------------------------
// PKCE (RFC 7636)
// -----------------------------------------------------------------------------

function aleatorioBase64Url(bytes: number): string {
  const buffer = new Uint8Array(bytes);
  crypto.getRandomValues(buffer);
  return base64Url(buffer);
}

/** El challenge es el SHA-256 del verifier. Viaja en /authorize; el verifier no. */
async function calcularChallenge(verifier: string): Promise<string> {
  const hash = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  return base64Url(new Uint8Array(hash));
}

function base64Url(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}
