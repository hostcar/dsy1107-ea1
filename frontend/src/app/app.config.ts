import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { ConfigService } from './config.service';
import { tokenInterceptor } from './token.interceptor';

/**
 * Los tres proveedores que importan en este ejercicio:
 *
 *   provideHttpClient(withInterceptors([...]))
 *       Registra el interceptor que agrega el header Authorization. Es el
 *       equivalente en Angular a lo que la actividad 1.3.2 hace con MSAL:
 *       ningun componente vuelve a escribir "Bearer" a mano.
 *
 *   provideAppInitializer(...)
 *       Angular no arranca hasta que config.json este leido. Sin esto, el
 *       primer render ocurriria sin saber a que Cognito hay que ir.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withInterceptors([tokenInterceptor])),
    provideAppInitializer(() => inject(ConfigService).cargar()),
  ],
};
