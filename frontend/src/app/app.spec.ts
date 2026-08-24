import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';

import { App } from './app';
import { AppConfig, ConfigService } from './config.service';

export const CONFIG_DE_PRUEBA: AppConfig = {
  region: 'us-east-1',
  cognitoDomain: 'https://dsy1107-ng-prueba.auth.us-east-1.amazoncognito.com',
  clientId: 'abc123',
  redirectUri: 'http://localhost:4200/',
  apiUrl: 'https://api123.execute-api.us-east-1.amazonaws.com',
};

describe('App', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [App], providers: [provideHttpClient()] });
  });

  it('sin config.json muestra la pantalla de ayuda, no el login', async () => {
    TestBed.inject(ConfigService).faltantes.set(['clientId']);

    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(texto).toContain('Falta configurar el front');
    expect(texto).toContain('clientId');
  });

  it('con config y sin sesion ofrece iniciar sesion en Cognito', async () => {
    TestBed.inject(ConfigService).actual.set(CONFIG_DE_PRUEBA);

    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(texto).toContain('Iniciar sesión con Cognito');
    expect(texto).not.toContain('Falta configurar');
  });
});
