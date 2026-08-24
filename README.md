# 1.2.9d — Login con Cognito y API protegida, en Angular

**DSY1107 · Desarrollo Cloud Native I · EA1 / Actividad 1.2**

**Código de apoyo:** `EA1/codigo/`

Cierra el arco de la EA1: la API de la actividad **1.1.2** (`mindicador.cl` detrás de un API Gateway) quedaba abierta a
todo el mundo. Aquí se le pone puerta con un **IDaaS** —Amazon Cognito— y se construye el front en **Angular** que abre
esa puerta con un token.

```
Navegador (Angular, :4200)
      │
      │ 1. login  ──────────────────────►  Cognito Hosted UI  (/oauth2/authorize)
      │ 2. code   ◄──────────────────────
      │ 3. code + code_verifier ────────►  Cognito            (/oauth2/token)
      │ 4. access_token + id_token ◄─────
      │
      ├─ 5. GET /oauth2/userInfo         ──►  Cognito          ← identidad (OIDC)
      ├─ 6. POST GetUser                 ──►  cognito-idp API  ← identidad (AWS)
      └─ 7. GET /datos  + Bearer token   ──►  API Gateway  ──►  https://mindicador.cl/api
                                              │
                                              └─ JWT Authorizer: verifica firma,
                                                 issuer, audiencia y expiración
                                                 contra /jwks. Sin token: 401.
```

El mismo access token sirve para las tres llamadas. Eso es lo que hace útil a un IDaaS: el permiso viaja en el token, no
en cada backend.

> **Es la versión Angular de la actividad.** La versión React se construye paso a paso en la guía **1.2.9b**,
> contra este mismo despliegue. Lo que cambia aquí está listado en «Angular en vez de React», más abajo: el protocolo es idéntico, el
> framework no.

---

## Antes de empezar: dónde se despliega esto

> **Amazon Cognito no está habilitado en el AWS Academy Learner Lab.** La cuenta del laboratorio restringe los servicios
> disponibles y `cognito-idp` queda fuera; `terraform apply` falla con `AccessDeniedException`.
>
> Compruébalo en un minuto antes de la clase:
>
> ```bash
> aws cognito-idp list-user-pools --max-results 1
> ```
>
> Si responde `AccessDenied`, este ejercicio se hace en una **cuenta AWS personal con capa gratuita**. Cognito incluye
> 10.000 usuarios activos al mes sin costo, y API Gateway HTTP API el primer millón de peticiones: el ejercicio completo
> cuesta 0 mientras se destruya al terminar.

Todo lo demás (la API Gateway, la integración con `mindicador.cl`) sí funciona en el Learner Lab, igual que en 1.1.2.

---

## Requisitos

| Herramienta   | Versión  | Para qué                                       |
|---------------|----------|------------------------------------------------|
| Terraform     | ≥ 1.5    | Desplegar Cognito y el API Gateway             |
| Node.js       | ≥ 20.19  | Angular 22 (`ng serve`)                        |
| Cuenta AWS    | —        | Con Cognito habilitado (ver arriba)            |
| Navegador     | —        | Cualquiera con DevTools: se usa la pestaña Red |

---

## Despliegue

```bash
cd terraform

# 1. Tus valores: apellido, usuario de prueba, contraseña.
cp terraform.tfvars.example terraform.tfvars
$EDITOR terraform.tfvars

terraform init
terraform plan          # revisa QUÉ se va a crear antes de crear nada
terraform apply

# 2. El front se configura solo con lo que imprime Terraform.
terraform output -raw config_frontend > ../frontend/public/config.json

cd ../frontend
npm install
npm start               # http://localhost:4200
```

Al terminar la clase:

```bash
cd terraform && terraform destroy
```

---

## Qué hace cada archivo

| Archivo                              | Contenido                                                                    |
|--------------------------------------|------------------------------------------------------------------------------|
| `terraform/cognito.tf`               | User pool, dominio del Hosted UI, cliente SPA (público, PKCE) y usuario demo |
| `terraform/apigateway.tf`            | La API de 1.1.2 + CORS + **JWT authorizer** + una ruta pública para comparar  |
| `terraform/variables.tf`             | Parámetros con validaciones (el dominio de Cognito es único a nivel mundial)  |
| `terraform/outputs.tf`               | URLs, IDs y el `config.json` listo para el front                              |
| `frontend/src/app/auth.service.ts`   | Authorization Code + PKCE escrito a mano: `/authorize`, `/token`, `/logout`   |
| `frontend/src/app/token.interceptor.ts` | Pone el header `Authorization` en un solo lugar, y decide a quién NO enviarlo |
| `frontend/src/app/api.service.ts`    | Las tres llamadas: `/userInfo`, `GetUser` y `/datos`                          |
| `frontend/src/app/config.service.ts` | Lee `public/config.json` antes de que la app arranque                         |
| `frontend/src/app/jwt.ts`            | Decodifica el payload del token para mostrarlo (1.2.7 / 1.2.8)                |
| `frontend/src/app/app.ts` + `app.html` | Pantalla: sesión, claims del token y los botones que prueban cada API       |

---

## Verificación

Lo que hay que ver, en este orden:

| # | Acción                                        | Resultado esperado                                                    |
|---|-----------------------------------------------|------------------------------------------------------------------------|
| 1 | `curl <url_datos_protegido>`                  | **401** con `{"message":"Unauthorized"}` — la puerta está cerrada      |
| 2 | `curl <url_datos_publico>`                    | **200** con los indicadores — la integración funciona                  |
| 3 | Botón «Iniciar sesión con Cognito»            | Redirige al Hosted UI; la contraseña se escribe **fuera** del front    |
| 4 | Vuelta a `localhost:4200`                     | La URL trae `?code=…` y la app la cambia por tokens                    |
| 5 | Tarjeta «Los tokens que devolvió el IDaaS»    | ID Token con `email`/`name`; Access Token con `scope` y `client_id`    |
| 6 | Botón `/oauth2/userInfo`                      | **200** con `sub`, `email`, `name`                                     |
| 7 | Botón `Cognito GetUser`                       | **200** con `UserAttributes` — la misma identidad, otra API            |
| 8 | Botón `/datos con token`                      | **200** con uf, dólar, euro, ipc, utm                                  |
| 9 | Botón `/datos sin token`                      | **401** — mismo endpoint, misma app, sin credencial                    |

Los pasos 8 y 9 son el ejercicio completo en dos clics: **la única diferencia entre ambos es el header
`Authorization`**.

El contador «el access token expira en mm:ss» de la tarjeta 1 llega a `0:00` a los 60 minutos. Cuando eso pasa, la
pantalla vuelve sola a ofrecer el login: el token caducó y el authorizer ya no lo aceptaría.

### Las pruebas también lo dicen

```bash
cd frontend && npm test
```

Nueve pruebas, y tres de ellas son las que interesan en seguridad: el interceptor agrega el token al API Gateway,
lo omite cuando la petición pide `SIN_TOKEN`, y **no lo envía a ningún otro destino**.

### Mirar el token por dentro

```bash
# Copia el access token desde la pantalla y decodifícalo (esto es 1.2.8):
echo '<access_token>' | cut -d. -f2 | base64 -d 2>/dev/null | python3 -m json.tool
```

Claims que importan: `iss` (quién lo emitió), `client_id` (a quién se lo emitió), `scope` (qué permite) y `exp`
(hasta cuándo). Son exactamente los cuatro que revisa el authorizer.

---

## Angular en vez de React: lo que cambia

| Tema                | React (Vite)                             | Angular                                                                 |
|---------------------|------------------------------------------|-------------------------------------------------------------------------|
| Puerto del dev server | 5173                                   | **4200** — y por eso cambian `origenes_frontend`, `callback_urls` y `logout_urls` |
| Configuración       | `.env.local` con `FE_*`, incrustada en el bundle | `public/config.json` leído en tiempo de ejecución con `provideAppInitializer` |
| Estado de la pantalla | `useState`                             | **Signals** (`signal`, `computed`); el proyecto es *zoneless*, sin `zone.js` |
| Llamadas HTTP       | `fetch` en cada función                  | `HttpClient` + **interceptor**: el header `Authorization` se pone en un solo lugar |
| Excepción al header | Se omitía a mano                         | `HttpContextToken SIN_TOKEN` — explícito en la petición que debe fallar   |
| Pruebas            | —                                        | `vitest` + `TestBed`: 9 pruebas, incluida la fuga de token                |

El interceptor es lo que hace este front más parecido a un proyecto real —y a lo que la actividad **1.3.2** hace con
MSAL: ningún componente escribe `Bearer` a mano.

Lo que **no** cambia: `/authorize`, `/token`, PKCE, los scopes, el JWT y el authorizer. Es el mismo protocolo con otro
framework, que es justamente el argumento de la presentación 1.2.1 sobre por qué OAuth2 y OIDC son **estándares**.

### Los dos despliegues pueden convivir

Los recursos de esta versión llevan el sufijo `-ng` (`dsy1107-ng-<apellido>`, `api-mindicador-ng-<apellido>`), así que
puedes tener la versión React y la Angular en la misma cuenta AWS al mismo tiempo. Cada una tiene su propio user pool,
su propio dominio de Hosted UI y su propia API: los tokens de una **no** sirven en la otra, y eso también se puede
mostrar en clase (el `iss` y el `client_id` no calzan → 401).

---

## Correspondencia con el material

| Material                                       | Aquí                                                             |
|------------------------------------------------|------------------------------------------------------------------|
| 1.1.2 · API Manager con `mindicador.cl`        | `apigateway.tf` — la misma integración `HTTP_PROXY`              |
| 1.1.4 · CORS                                   | `cors_configuration`, ahora obligatorio: quien llama es un navegador |
| 1.2.1 · OAuth2 y OIDC                          | `auth.service.ts` — `/authorize`, `/token`, scopes, PKCE         |
| 1.2.2 · IDaaS y CIAM                           | El user pool: directorio + servidor de autorización               |
| 1.2.2b · Anatomía de un login real             | `discovery_url` en los outputs: el mismo `/.well-known` del ejercicio |
| 1.2.3 · Configurando un Tenant                 | `aws_cognito_user_pool` (el tenant, en código)                   |
| 1.2.4 · Configurando apps en un IdaaS          | `aws_cognito_user_pool_client` (cliente público, sin secreto)    |
| 1.2.6 · Integrando seguridad en el API Manager | `aws_apigatewayv2_authorizer` con `jwt_configuration`            |
| 1.2.7 / 1.2.8 · JWT y claims                   | `jwt.ts` y la tarjeta que muestra los claims decodificados        |
| 1.3.2 · Configurar MSAL en el frontend         | `token.interceptor.ts`: el equivalente en Angular + AWS          |

> El material oficial usa **Microsoft Entra External ID + MSAL**. Aquí se hace con **Cognito** por coherencia con el
> API Gateway de AWS que ya se construyó en 1.1.2 y porque el JWT authorizer lo valida de forma nativa. Cambiando
> `cognitoDomain` y `clientId` en `config.json` por los de Entra, `auth.service.ts` funciona igual.

---

## Problemas frecuentes

| Síntoma                                            | Causa                                                             | Solución                                                                 |
|----------------------------------------------------|-------------------------------------------------------------------|--------------------------------------------------------------------------|
| El front dice «Falta configurar el front»          | No existe `public/config.json`                                    | `terraform output -raw config_frontend > ../frontend/public/config.json` y recarga |
| `redirect_mismatch` en el Hosted UI                | `redirect_uri` distinto del `callback_urls` de Cognito            | Deben coincidir carácter a carácter, **incluida la barra final**          |
| El login funciona en 4200 pero no en otro puerto   | `ng serve` cambió de puerto                                       | `npm start` fija el 4200; si lo cambias, cambia también los tres valores del `.tfvars` y vuelve a aplicar |
| `/token` responde **400 invalid_grant**            | El `code` ya se usó, o se perdió el `code_verifier`               | Los codes son de un solo uso; vuelve a iniciar sesión                    |
| `/datos` responde **401** con sesión iniciada      | Token expirado (dura 60 min) o se envió el ID token               | Envía el **access** token; vuelve a iniciar sesión                       |
| `/datos` responde **403** `Forbidden`              | El token no trae el scope exigido por `authorization_scopes`       | Revisa que `scope` incluya `openid` en el access token                   |
| Error de red al llamar a `/datos` desde el front   | CORS: falta el origen o el header `authorization`                 | `cors_configuration` en `apigateway.tf`; recuerda el preflight OPTIONS   |
| `InvalidParameterException: domain already exists` | El dominio de Cognito es único a nivel mundial                    | Cambia `estudiante` en `terraform.tfvars`                                |
| `AccessDeniedException` al crear el user pool      | Estás en AWS Academy                                              | Cognito no está habilitado ahí; usa cuenta personal (ver arriba)         |

---

## Hasta dónde llega y hasta dónde no

Lo que este ejercicio **sí** demuestra: el front nunca ve la contraseña, el token lo emite un tercero de confianza, y el
API Gateway rechaza lo que no venga firmado por ese tercero.

Lo que **no** hace, y conviene decirlo en voz alta en clase:

- **Los tokens se guardan en `sessionStorage`.** Es accesible desde JavaScript: un XSS los lee. Se usa por simplicidad
  didáctica; en producción se mitiga con tokens cortos (aquí, 60 min), revocación y —mejor— un backend-for-frontend que
  guarde el refresh token en una cookie `HttpOnly`.
- **No hay refresh automático.** Cuando el token expira, el usuario vuelve a iniciar sesión.
- **La ruta `/publico/datos` existe solo para comparar.** En un sistema real se elimina.

---

## Extensiones

1. **Refresh token**: usar `grant_type=refresh_token` en `/token` antes de que el contador llegue a `0:00`. El lugar
   natural es un método más en `auth.service.ts`, y el interceptor no cambia.
2. **Guard de ruta**: agregar el router y un `canActivate` que exija sesión. Es el patrón que se usa en producción para
   proteger vistas, no solo APIs.
3. **Scopes propios**: crear un `aws_cognito_resource_server` con `indicadores.read` y exigirlo en
   `authorization_scopes`. Ahí se ve la autorización granular de la que habla 1.2.1.
4. **Login social**: agregar Google como identity provider del user pool. El front no cambia ni una línea.
