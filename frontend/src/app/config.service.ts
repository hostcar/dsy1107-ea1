import { Injectable, computed, signal } from '@angular/core';

/** Lo que Terraform imprime en `terraform output -raw config_frontend`. */
export interface AppConfig {
  /** Region de AWS. El issuer del token y la API de Cognito dependen de ella. */
  region: string;
  /** Dominio del Hosted UI: aqui viven /oauth2/authorize, /oauth2/token y /oauth2/userInfo. */
  cognitoDomain: string;
  /** Client ID de la SPA. Publico por definicion: el navegador lo envia en cada /authorize. */
  clientId: string;
  /** Debe coincidir EXACTAMENTE con callback_urls del user pool client, barra final incluida. */
  redirectUri: string;
  /** URL del API Gateway de la actividad 1.1.2. */
  apiUrl: string;
}

const CLAVES: readonly (keyof AppConfig)[] = [
  'region',
  'cognitoDomain',
  'clientId',
  'redirectUri',
  'apiUrl',
];

/**
 * Configuracion en tiempo de ejecucion.
 *
 * Angular compila a archivos estaticos: en el navegador no existe process.env
 * ni import.meta.env. Se puede incrustar la configuracion en el bundle
 * (src/environments), pero entonces cambiar de tenant obliga a recompilar.
 * Aqui se lee un JSON servido junto a la app, que es lo que se hace en un
 * despliegue real y ademas se puede abrir y mirar en clase.
 */
@Injectable({ providedIn: 'root' })
export class ConfigService {
  /** null mientras no haya un config.json valido. */
  readonly actual = signal<AppConfig | null>(null);

  /** Claves ausentes o sin reemplazar. La pantalla de ayuda las muestra. */
  readonly faltantes = signal<readonly string[]>([]);

  readonly listo = computed(() => this.actual() !== null);

  /** La llama provideAppInitializer antes del primer render. */
  async cargar(): Promise<void> {
    // Relativo al <base href>, para que funcione igual servido en una subruta.
    const url = new URL('config.json', document.baseURI);

    try {
      const respuesta = await fetch(url, { cache: 'no-store' });

      if (!respuesta.ok) {
        this.faltantes.set([`public/config.json responde HTTP ${respuesta.status}`]);
        return;
      }

      const datos = (await respuesta.json()) as Partial<AppConfig>;
      const faltan = CLAVES.filter((clave) => {
        const valor = datos[clave];
        return !valor || String(valor).includes('xxxx');
      });

      if (faltan.length > 0) {
        this.faltantes.set(faltan);
        return;
      }

      this.actual.set(datos as AppConfig);
    } catch (error) {
      this.faltantes.set([`no se pudo leer public/config.json (${(error as Error).message})`]);
    }
  }

  /** Para el codigo que solo corre con sesion iniciada: si falta, es un bug. */
  get valor(): AppConfig {
    const valores = this.actual();
    if (!valores) {
      throw new Error('Configuracion no cargada: falta public/config.json');
    }
    return valores;
  }
}
