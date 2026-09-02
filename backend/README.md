# El backend que va detrás del API Manager

**DSY1107 · Desarrollo Cloud Native I · EA1 / Actividad 1.1**

Spring Boot 4.1 · Java 21 · `cl.duoc.dsy1107:ae1`

En la actividad **1.1.2** el API Gateway integraba directo contra `https://mindicador.cl/api`. Ese es el caso más
simple de un API Manager: publicar una API de un tercero. Este servicio ocupa ese lugar —expone `/datos` con
exactamente la misma respuesta— pero agrega lo que una API pública ajena no te puede dar.

```
Navegador (Angular, :4200)
      │
      │  GET /datos  + Bearer token
      ▼
API Gateway  ── JWT Authorizer ──►  Cognito (/jwks)      ← esto es 1.2.6
      │
      │  la petición ya viene autorizada
      ▼
ESTE BACKEND  ─── ¿lo tengo en caché? ──► sí: responde sin salir a la red   (X-Cache: HIT)
                                    └──► no: GET https://mindicador.cl/api  (X-Cache: MISS)
                                         y si el origen se cayó, entrega
                                         la copia vieja antes que un error   (X-Cache: STALE)
```

## Lo que este backend NO hace, a propósito

**No valida el token.** No trae `spring-boot-starter-oauth2-resource-server`, no mira el header `Authorization`, no
sabe qué es Cognito. De eso se encarga el JWT authorizer del API Gateway (`terraform/apigateway.tf`).

Eso no es un descuido: es el modelo del API Manager en su forma pura. **La seguridad vive en el borde** y el servicio
de atrás se dedica a su negocio. Es la razón por la que en la arquitectura de la EA1 el 401 nunca llega hasta acá —
la petición sin token muere en el borde, y este proceso ni se entera.

En producción a esto se le agrega una segunda capa (el backend revalida el token por su cuenta, por si alguien alcanza
el puerto 8080 saltándose el gateway). Está anotado abajo, en «Hasta dónde llega».

---

## Levantarlo

```bash
cd backend

# Las tres variables de la base son OBLIGATORIAS: sin ellas el proceso no
# arranca. Salen de los outputs de Terraform, así que no hay que copiarlas.
export SPRING_DATASOURCE_URL="$(terraform -chdir=../terraform output -raw db_jdbc_url)"
export SPRING_DATASOURCE_USERNAME="$(terraform -chdir=../terraform output -raw db_usuario)"
export SPRING_DATASOURCE_PASSWORD='...'      # la de terraform.tfvars

./mvnw spring-boot:run       # http://localhost:8080
```

> El RDS es **público** (ver `terraform/rds.tf`), así que el backend local se conecta a la misma base que usa la task de
> ECS. Es cómodo para la clase y conviene decirlo en voz alta: dos personas desarrollando a la vez comparten las filas.

Las **pruebas no necesitan nada de esto**. Como `application.yml` no fija ninguna URL, en el classpath de test no hay
datasource configurado y Spring Boot levanta una H2 en memoria por su cuenta. `./mvnw test` corre sin RDS y sin Docker.

O el jar, que es lo que construye el CI (`.github/workflows/backend.yml`):

```bash
mvn verify
java -jar target/ae1-0.0.1-SNAPSHOT.jar
```

> En esta máquina no hay `mvn` en el PATH, pero sí una distribución de Maven descargada por el wrapper:
> `~/.m2/wrapper/dists/apache-maven-3.9.14-bin/*/apache-maven-3.9.14/bin/mvn`. Con eso se compila y se prueba igual.

---

## Endpoints

| Método   | Ruta                 | Qué hace                                                                   |
|----------|----------------------|-----------------------------------------------------------------------------|
| `GET`    | `/datos`             | Todos los indicadores del día. **Es la ruta que consume el front**           |
| `GET`    | `/datos/{indicador}` | Uno solo, con su serie: `/datos/uf`, `/datos/dolar`, `/datos/utm`…           |
| `GET`    | `/actuator/health`   | `{"status":"UP"}`. **Es el health check del entorno de Beanstalk** (guía 1.3.8, lámina 16) |
| `GET`    | `/cache`             | Estado de la caché: aciertos, llamadas a la red, qué hay guardado            |
| `DELETE` | `/cache`             | Vacía la caché para forzar una llamada al origen. Existe para la demo        |
| `POST`   | `/productos`         | Crea un producto. `201` + `Location`                                         |
| `GET`    | `/productos`         | Lista todos, del más nuevo al más viejo                                      |
| `GET`    | `/productos/{id}`    | Uno solo. `404` si no está                                                   |
| `PUT`    | `/productos/{id}`    | Reemplaza nombre, precio y stock. `creadoEn` no se toca                      |
| `DELETE` | `/productos/{id}`    | Borra. `204` sin cuerpo, o `404` si no estaba                                |

`/actuator/health` y `GET /cache` responden preguntas distintas, y conviene no confundirlas: el primero dice si el
proceso puede atender; el segundo, cuánta red se está ahorrando. Este backend puede estar `UP` y a la vez sirviendo
copias vencidas porque `mindicador.cl` se cayó.

Toda respuesta de `/datos` lleva dos cabeceras que normalmente son invisibles:

| Cabecera       | Valores            | Significado                                                    |
|----------------|--------------------|-----------------------------------------------------------------|
| `X-Cache`      | `MISS` / `HIT` / `STALE` | Hubo red / no hubo red / el origen falló y se sirvió la copia vieja |
| `X-Cache-Edad` | segundos           | Cuánto lleva guardado el dato que se está entregando            |

Son los mismos nombres que usa cualquier CDN. No es vocabulario inventado para la clase.

---

## La demo, en cuatro `curl`

```bash
# 1. Primera petición: sale a la red.
curl -s -D - -o /dev/null localhost:8080/datos | grep -i x-cache
#    X-Cache: MISS
#    X-Cache-Edad: 0

# 2. La misma petición otra vez: ya no sale.
curl -s -D - -o /dev/null localhost:8080/datos | grep -i x-cache
#    X-Cache: HIT

# 3. ¿Cuántas veces se llamó realmente al origen?
curl -s localhost:8080/cache
#    {"origen":"https://mindicador.cl/api","ttlSegundos":600,
#     "aciertos":1,"llamadasARed":1,"respuestasVencidas":0, ...}

# 4. Se vacía la caché y vuelve a haber red.
curl -s -X DELETE localhost:8080/cache && curl -s -D - -o /dev/null localhost:8080/datos | grep -i x-cache
#    {"entradasEliminadas":1}
#    X-Cache: MISS
```

Con 40 personas en la sala llamando a la vez, la diferencia entre `MISS` y `HIT` son 40 peticiones a `mindicador.cl`
contra 1. **Ese es el argumento de por qué existe esta capa**, y se ve en el contador de `GET /cache` sin necesidad de
creerle a nadie.

Para verlo vencer en vivo, bajar el TTL antes de levantar:

```bash
BACKEND_MINDICADOR_TTL=30s mvn spring-boot:run
```

Otras rutas que vale la pena mostrar:

```bash
curl -s localhost:8080/datos/uf | head -c 200      # 200: está en la lista blanca
curl -s -w '\nHTTP %{http_code}\n' localhost:8080/datos/pesos
#   404 — y de paso dice cuáles indicadores sí existen. Nunca salió a la red.
```

---

## La persistencia, y por qué vive en este mismo proceso

El CRUD de `/productos` comparte proceso, imagen y task con el proxy de indicadores. Separarlo en un servicio aparte
habría agregado un cluster, un registro y una integración más al ejercicio sin enseñar nada que la EA1 no enseñe ya.

Tres decisiones que conviene defender en clase:

**La entidad no sale por HTTP.** `Producto` es una clase JPA; lo que viaja al cliente son los `record` `ProductoNuevo` y
`ProductoVista`. La conversión se escribe a mano. Publicar la entidad directo es cómodo cinco minutos y después caro:
cualquier cambio del esquema se convierte en un cambio de la API pública sin que nadie lo haya decidido. Y al revés,
`ProductoNuevo` no tiene `id` ni `creadoEn` — lo que no está en el `record` no se puede escribir, así que el cliente no
puede elegir su propio id ni falsear la fecha de creación.

**El esquema lo manda Flyway, no Hibernate.** `ddl-auto` está en `validate`: al arrancar comprueba que entidades y
tablas coincidan, y si no, se cae con un mensaje claro. Con `update`, renombrar un campo en Java crea una columna nueva
y deja la vieja con todos los datos dentro, sin decir nada.

**La migración está escrita en SQL estándar**, no en el dialecto de PostgreSQL:

| Estándar                                  | Dialecto de PostgreSQL |
|-------------------------------------------|------------------------|
| `BIGINT GENERATED BY DEFAULT AS IDENTITY` | `BIGSERIAL`            |
| `TIMESTAMP WITH TIME ZONE`                | `TIMESTAMPTZ`          |

Las dos formas hacen lo mismo en PostgreSQL, pero solo las primeras las entiende también H2. Así **la misma migración
corre en producción y en las pruebas**, y `ProductoControllerTest` verifica la migración de verdad en vez de saltársela
con un esquema inventado por Hibernate.

> **Trampa de Spring Boot 4.** `flyway-core` en el `pom.xml` no basta: Boot 4 partió las autoconfiguraciones en módulos
> y la de Flyway ya no viaja dentro de `spring-boot-autoconfigure`. Sin `org.springframework.boot:spring-boot-flyway`,
> la librería queda en el classpath y **nadie la invoca** — sin error y sin log. La primera señal es un
> `Schema validation: missing table [producto]` que apunta a Hibernate y no a Flyway, que es donde está el problema.

---

## Qué hace cada archivo

| Archivo                        | Contenido                                                                       |
|--------------------------------|----------------------------------------------------------------------------------|
| `Ae1Application.java`          | El `main`. Los comentarios de arriba explican el rol del componente               |
| `config/BackendProperties.java`| Todo lo configurable, tipado: URL del origen, TTL, timeouts, lista blanca, CORS   |
| `config/BackendConfig.java`    | Los tres beans: el `RestClient` **con timeouts**, el `Clock` y la caché           |
| `config/CorsConfig.java`       | CORS solo para el modo local (1.1.4). En AWS lo resuelve el API Gateway           |
| `cache/CacheTtl.java`          | La caché con vencimiento, escrita a mano: cuarenta líneas que se leen enteras     |
| `indicadores/IndicadoresService.java` | La lógica completa: HIT → MISS → STALE → 502                              |
| `indicadores/IndicadoresController.java` | Las rutas y las cabeceras `X-Cache`                                    |
| `web/ManejadorDeErrores.java`  | Traduce las excepciones de **todos** los dominios a JSON: 404, 502 y 400        |
| `productos/Producto.java`      | La entidad JPA. No sale por HTTP: el controlador convierte a `record`            |
| `productos/ProductoRepository.java` | `JpaRepository`. Ni una línea de código: la implementa Spring Data         |
| `productos/ProductoService.java` | Las cinco operaciones, sin saber que existe HTTP                              |
| `productos/ProductoController.java` | Las rutas del CRUD y los `record` de entrada y salida                      |
| `resources/db/migration/`      | El esquema, versionado con Flyway                                               |
| `Procfile`                     | Cómo arranca Beanstalk el jar. Va en la raíz del bundle, sin extensión            |
| `resources/application.yml`    | Los valores por defecto, todos pisables por variable de entorno                   |

---

## Las pruebas

```bash
./mvnw test       # 29 pruebas
```

| Clase                        | Qué asegura                                                                     |
|------------------------------|----------------------------------------------------------------------------------|
| `CacheTtlTest`               | Que el TTL vence, que la entrada vencida **no se borra**, que cada clave vence sola |
| `IndicadoresServiceTest`     | Que la segunda petición **no toca la red**, que vencido el TTL sí, que un origen caído devuelve la copia vieja y que sin copia devuelve 502 |
| `IndicadoresControllerTest`  | Que cada situación sale con su código y sus cabeceras: 200 + `HIT`, 200 + `STALE` + `no-cache`, 404, 502 |
| `ProductoControllerTest`     | El CRUD entero contra una base real (H2): 201 con `Location`, 404 en las tres rutas, 400 campo por campo |
| `Ae1ApplicationTests`        | Que el contexto levanta y `application.yml` queda bien enlazado                    |

Dos decisiones de diseño existen **solo** para que estas pruebas sean posibles, y conviene señalarlas en clase:

1. **`CacheTtl` recibe un `java.time.Clock`** en vez de llamar a `Instant.now()`. Sin eso, probar un TTL de 10 minutos
   obligaría a dormir el test 10 minutos. Con eso, `RelojDePrueba` adelanta la hora y el test tarda 65 ms.
2. **`MockRestServiceServer` se mete dentro del `RestClient`** y cuenta las llamadas. La aserción de
   `segundaLlamadaDesdeCache` no es sobre el valor devuelto: es `ExpectedCount.once()`. Si el servicio saliera dos veces
   a la red, el test falla aunque la respuesta sea correcta.

---

## Actuator: por qué está, y por qué hace tan poco

`spring-boot-starter-actuator` está por una razón concreta de la guía **1.3.8**: el entorno de Elastic Beanstalk sondea
`Application Healthcheck URL`, y si se deja el valor por defecto pide `/` —que aquí no existe— recibe 404 y marca el
entorno **Severe** con la aplicación funcionando perfectamente. Apuntándolo a `/actuator/health` eso desaparece.

Está configurado para hacer lo mínimo, y las tres decisiones son deliberadas:

| Ajuste                          | Valor    | Por qué                                                                    |
|---------------------------------|----------|-----------------------------------------------------------------------------|
| `exposure.include`              | `health` | Nada más sale por HTTP. `/actuator/env` y `/actuator/beans` filtran configuración y estructura interna, y este backend **no tiene puerta**: lo que se exponga queda al alcance de quien alcance el puerto |
| `show-details`                  | `never`  | La respuesta es exactamente `{"status":"UP"}`, la de la lámina 9            |
| `health.probes.enabled`         | `false`  | Sin esto Boot 4 agrega los grupos `liveness`/`readiness` de Kubernetes y la respuesta pasa a ser `{"groups":[...],"status":"UP"}`. Aquí no hay Kubernetes |

**Lo que a propósito no hay: un `HealthIndicator` que le pegue a `mindicador.cl`.** Es tentador —«el health check debería
comprobar mis dependencias»— y es justo al revés. Si el `/actuator/health` de este proceso se pusiera `DOWN` cuando el
origen se cae, Beanstalk declararía enferma —y terminaría reemplazando— una instancia que está respondiendo 200 con la
copia en caché. Un health check contesta *«puedo atender»*, no *«mi proveedor está bien»*. Toda la lógica `STALE` de
este backend existe precisamente para seguir atendiendo sin el origen: hacerla invisible al health check sería
contradecirla.

---

## Configuración

Todo en `application.yml`, y todo pisable por variable de entorno —que es como se configurará el día que esto corra en
un contenedor:

| Propiedad                       | Variable de entorno              | Por defecto                 |
|---------------------------------|----------------------------------|-----------------------------|
| `backend.mindicador.url`        | `BACKEND_MINDICADOR_URL`         | `https://mindicador.cl/api` |
| `backend.mindicador.ttl`        | `BACKEND_MINDICADOR_TTL`         | `10m`                       |
| `backend.mindicador.conectar-en`| `BACKEND_MINDICADOR_CONECTAREN`  | `3s`                        |
| `backend.mindicador.leer-en`    | `BACKEND_MINDICADOR_LEEREN`      | `10s`                       |
| `backend.cors.origenes`         | `BACKEND_CORS_ORIGENES`          | `:4200` y `:5173`           |
| `server.port`                   | `SERVER_PORT`                    | `8080`                      |
| `spring.datasource.url`         | `SPRING_DATASOURCE_URL`          | **ninguno**                 |
| `spring.datasource.username`    | `SPRING_DATASOURCE_USERNAME`     | **ninguno**                 |
| `spring.datasource.password`    | `SPRING_DATASOURCE_PASSWORD`     | **ninguno**                 |

Los dos timeouts no son decoración. Un backend sin timeout de lectura **hereda la lentitud del servicio del que
depende**: si el origen queda colgado, este proceso queda colgado con él, después el API Gateway (que corta a los 29 s)
y después el navegador. Cortar temprano y entregar la copia en caché es exactamente lo que hace `IndicadoresService`.

---

## Correspondencia con el material

| Material                                | Aquí                                                                    |
|-----------------------------------------|--------------------------------------------------------------------------|
| 1.1.1 · Qué es un API Manager           | Este servicio es el «backend» del diagrama: lo que el API Manager publica |
| 1.1.2 · API Manager con `mindicador.cl` | La misma API, ahora con una capa propia delante                          |
| 1.1.4 · CORS                            | `CorsConfig` — el equivalente en Spring del `cors_configuration` de Terraform |
| 1.2.6 · Seguridad en el API Manager     | Por contraste: la seguridad **no** está acá, está en el borde            |
| 1.3.8 · Desplegar en Elastic Beanstalk  | `actuator`, `<finalName>backend</finalName>` y el `Procfile`             |

---

## Hasta dónde llega y hasta dónde no

Lo que este backend **sí** demuestra: que la capa de atrás del API Manager es un servicio común y corriente, que puede
cachear, tolerar fallas del origen y hacerlo visible con dos cabeceras.

Lo que **no** hace, y conviene decirlo en voz alta:

- **No valida el token.** Confía por completo en el authorizer del API Gateway. Si alguien alcanza el puerto 8080
  directamente, entra. La mitigación real es doble validación: agregar `spring-boot-starter-oauth2-resource-server`
  apuntando al mismo issuer de Cognito, y además no exponer el puerto fuera de la red del gateway.
- **La caché es en memoria y por proceso.** Con dos instancias hay dos cachés, y `DELETE /cache` vacía solo una. Con
  Redis dejaría de ser cierto.
- **`DELETE /cache` está abierto.** Solo bota copias de datos públicos, pero en un sistema real iría protegido o no
  existiría.
- **El componente está listo para Beanstalk, pero todavía no se despliega solo.** El `pom.xml` y el `Procfile` ya
  cumplen lo que pide la guía 1.3.8; lo que falta vive fuera de `backend/`: `terraform/beanstalk.tf`, el
  `scripts/publicar-beanstalk.sh` y el paso de despliegue al final de `backend.yml`.
- **`java.version` es 21, y tiene que seguir igual a la plataforma de Beanstalk.** La cuenta ofrece Corretto 21 y 25;
  se usa 21, que es lo que fija la lámina 7 y lo que declara `terraform/beanstalk.tf`. La razón no es solo de
  compatibilidad: con la plataforma de Corretto 25 —la más nueva de AWS— el arranque de la instancia se pasó dos veces
  de los 18 minutos y mató el `LaunchWaitCondition`. Si cambias uno de los tres sitios (`pom.xml`, `beanstalk.tf`,
  `backend.yml`), cambia los tres, o el arranque muere con `UnsupportedClassVersionError`.

Y un detalle que aparece al conectarlo con lo ya construido: **`terraform/apigateway.tf` no expone `X-Cache` al
navegador.** Su `cors_configuration` declara `allow_headers` (los de la petición) pero no `expose_headers`, así que a
través del API Gateway el JavaScript del front recibe la respuesta pero no puede leer la cabecera. En local, apuntando
al `:8080`, sí la lee: `CorsConfig` la expone. Es el mismo tipo de detalle que el de la barra final en
`callback_urls`, y da para una pregunta de quiz.

---

## Extensiones

1. **Doble validación del token**: agregar el resource server y comprobar que la petición directa al `:8080` sin token
   pasa a responder 401, igual que la del gateway. Es 1.2.6 visto desde el otro lado.
2. **Caché distribuida**: reemplazar `CacheTtl` por Redis y levantar dos instancias. Ahí se ve por qué una caché por
   proceso no escala.
3. **Circuit breaker**: hoy cada petición reintenta contra un origen caído. Con Resilience4j se deja de insistir
   durante un rato, que es lo que hace un sistema serio.
4. **Apuntar el API Gateway acá**: cambiar `var.backend_url` en Terraform por la URL de este servicio ya desplegado. El
   front no cambia ni una línea —expone la misma ruta `/datos`— y el authorizer tampoco.
