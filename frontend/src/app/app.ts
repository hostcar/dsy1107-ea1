import { JsonPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';

import { ApiService, Resultado } from './api.service';
import { AuthService } from './auth.service';
import { ConfigService } from './config.service';

@Component({
  selector: 'app-root',
  imports: [JsonPipe],
  templateUrl: './app.html',
})
export class App {
  protected readonly auth = inject(AuthService);
  protected readonly cfg = inject(ConfigService);
  private readonly api = inject(ApiService);

  protected readonly resultado = signal<Resultado | null>(null);
  protected readonly cargando = signal(false);

  /** Cuenta atras del access token, en mm:ss. */
  protected readonly expiraEn = computed(() => {
    const total = this.auth.segundosRestantes();
    const minutos = Math.floor(total / 60);
    const segundos = total % 60;
    return `${minutos}:${String(segundos).padStart(2, '0')}`;
  });

  constructor() {
    // Si volvemos del Hosted UI, la URL trae ?code=: hay que canjearlo.
    void this.auth.procesarRetorno();
  }

  protected iniciarSesion(): void {
    void this.auth.login();
  }

  protected cerrarSesion(): void {
    this.auth.logout();
  }

  protected async llamar(peticion: () => Promise<Resultado>): Promise<void> {
    this.cargando.set(true);
    this.resultado.set(await peticion());
    this.cargando.set(false);
  }

  // Envoltorios para que la plantilla no toque el servicio directamente.
  protected userInfo = () => this.api.userInfo();
  protected getUser = () => this.api.getUser();
  protected datosConToken = () => this.api.indicadores(true);
  protected datosSinToken = () => this.api.indicadores(false);
  protected datosPublicos = () => this.api.indicadoresPublicos();
}
