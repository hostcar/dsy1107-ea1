import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CONFIG_DE_PRUEBA } from './app.spec';
import { AuthService } from './auth.service';
import { ConfigService } from './config.service';
import { SIN_TOKEN, tokenInterceptor } from './token.interceptor';

/**
 * Estas tres pruebas cubren la regla completa del interceptor. La tercera es la
 * que importa en seguridad: un token solo se envia a quien confiamos.
 */
describe('tokenInterceptor', () => {
  let http: HttpClient;
  let mock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([tokenInterceptor])),
        provideHttpClientTesting(),
      ],
    });

    TestBed.inject(ConfigService).actual.set(CONFIG_DE_PRUEBA);
    TestBed.inject(AuthService).tokens.set({
      access_token: 'token-de-prueba',
      id_token: 'id-de-prueba',
      expires_in: 3600,
      token_type: 'Bearer',
    });

    http = TestBed.inject(HttpClient);
    mock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => mock.verify());

  it('agrega el Bearer a las peticiones al API Gateway', () => {
    http.get(`${CONFIG_DE_PRUEBA.apiUrl}/datos`).subscribe();

    const peticion = mock.expectOne(`${CONFIG_DE_PRUEBA.apiUrl}/datos`);
    expect(peticion.request.headers.get('Authorization')).toBe('Bearer token-de-prueba');
    peticion.flush({});
  });

  it('no lo agrega cuando la peticion pide SIN_TOKEN (el boton del 401)', () => {
    http
      .get(`${CONFIG_DE_PRUEBA.apiUrl}/datos`, {
        context: new HttpContext().set(SIN_TOKEN, true),
      })
      .subscribe({ error: () => undefined });

    const peticion = mock.expectOne(`${CONFIG_DE_PRUEBA.apiUrl}/datos`);
    expect(peticion.request.headers.has('Authorization')).toBe(false);
    peticion.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });
  });

  it('no envia el token a destinos que no son el API Gateway ni Cognito', () => {
    http.post('https://cognito-idp.us-east-1.amazonaws.com/', {}).subscribe();

    const peticion = mock.expectOne('https://cognito-idp.us-east-1.amazonaws.com/');
    expect(peticion.request.headers.has('Authorization')).toBe(false);
    peticion.flush({});
  });
});
