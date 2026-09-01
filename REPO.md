# Estructura del repositorio — DSY1107 · EA1

Este documento define **cómo se organiza el repositorio de la Experiencia de Aprendizaje 1**. Respetar esta estructura
no es un capricho de orden: los pipelines de GitHub Actions buscan las carpetas por su nombre exacto. Si mueves o
renombras una, el pipeline deja de compilar.

## Estructura

```
/
├── .github/
│   └── workflows/          # pipelines de GitHub Actions (ver más abajo)
├── backend/                # API REST en Spring Boot (Java 21, Maven)
│   ├── src/main/java/      # código
│   ├── src/test/java/      # pruebas
│   ├── Dockerfile          # imagen que se publica en ECS
│   └── pom.xml             # dependencias y build
├── frontend/               # SPA en Angular o React
│   ├── src/                # código
│   ├── public/             # estáticos; aquí se escribe config.json en despliegue
│   └── package.json        # dependencias y scripts npm
├── terraform/              # infraestructura como código (AWS)
│   ├── cognito.tf          # IDaaS: user pool, cliente, dominio
│   ├── apigateway.tf       # API Manager: rutas y autorizador JWT
│   ├── amplify.tf          # hosting del frontend
│   ├── variables.tf        # entradas parametrizables
│   └── outputs.tf          # datos que consume el frontend (ids, URLs)
├── scripts/                # automatización del despliegue
├── .gitignore
└── README.md               # qué hace tu solución y cómo levantarla
```

Las carpetas `backend`, `frontend` y `terraform` son **obligatorias**. Si una parte no la alcanzaste a desarrollar, deja
la carpeta con lo que tengas: un repositorio sin `terraform/`
se evalúa como infraestructura no entregada.

## Qué va en cada carpeta

| Carpeta      | Contiene                                                      | No contiene                                               |
|:-------------|:--------------------------------------------------------------|:----------------------------------------------------------|
| `backend/`   | El servicio Spring Boot que queda **detrás** del API Gateway. | `target/`, credenciales, el `.jar` compilado.             |
| `frontend/`  | La aplicación Angular que hace el login contra Cognito.       | `node_modules/`, `dist/`, el `config.json` generado.      |
| `terraform/` | Los `.tf` que crean la infraestructura, y `*.tfvars.example`. | `.terraform/`, `terraform.tfstate`, tus `.tfvars` reales. |
| `scripts/`   | Scripts de apoyo al despliegue, ejecutables (`chmod +x`).     | Claves de AWS incrustadas en el código.                   |

## Secretos del repositorio

Los pipelines de despliegue necesitan credenciales de AWS, y esas **no se escriben en el código**: se guardan como
secretos del repositorio, en

`Settings` → `Secrets and variables` → `Actions` → `New repository secret`

Hay que crear estos tres:

| Secreto                 | De dónde sale                           |
|:------------------------|:----------------------------------------|
| `AWS_ACCESS_KEY_ID`     | Learner Lab → `AWS Details` → `AWS CLI` |
| `AWS_SECRET_ACCESS_KEY` | Learner Lab → `AWS Details` → `AWS CLI` |
| `AWS_SESSION_TOKEN`     | Learner Lab → `AWS Details` → `AWS CLI` |

**Se vencen cada vez que levantas el laboratorio.** Las credenciales del Learner Lab viven solo mientras dura la sesión,
así que hay que **actualizar los tres secretos** al empezar a trabajar. Si un pipeline de despliegue falla con un error
de autenticación o de token expirado, esta es la causa en la mayoría de los casos.

## Rama de trabajo

**Todo el trabajo va en `main`.** Es la rama que disparan los pipelines y la única que se revisa al evaluar: si lo que
hiciste no está en `main`, la evaluación ve un repositorio vacío y las insignias no marcan nada, aunque el código exista
en otra rama.

Puedes usar ramas de apoyo mientras desarrollas, pero **antes del cierre de la entrega todo debe estar integrado en
`main`**. No se revisan ramas sueltas, ni Pull Requests sin fusionar.

Si tu repositorio quedó con la rama `master` —pasa cuando se crea con una configuración antigua de git— renómbrala:

```bash
git branch -m master main          # renombra la rama local
git push -u origin main            # publica main y la deja como upstream
```

Luego, en GitHub: `Settings` → `General` → `Default branch`, cambia la rama predeterminada a `main`. Recién ahí puedes
borrar la antigua, porque GitHub no deja eliminar la rama predeterminada:

```bash
git push origin --delete master
```

Verifica en qué rama estás con `git branch --show-current`.

## Pipelines

Los workflows viven en `.github/workflows/` y **el nombre del archivo importa**: la tabla de seguimiento del curso
construye las insignias a partir de él. Nombres exactos:

| Archivo                | Qué hace                                     | Cuándo corre                  |
|:-----------------------|:---------------------------------------------|:------------------------------|
| `frontend_compile.yml` | `npm ci` + `npm run build` del frontend.     | Push que toque `frontend/**`. |
| `frontend_deploy.yml`  | Publica el frontend compilado en Amplify.    | Manual o push a `main`.       |
| `backend_compile.yml`  | Compila y prueba el backend con Maven.       | Push que toque `backend/**`.  |
| `backend_deploy.yml`   | Construye la imagen y la publica en Fargate. | Manual o push a `main`.       |

Los de despliegue son los que consumen los secretos de la sección anterior. Un pipeline que nunca se ha ejecutado
aparece como `no status`, igual que si no existiera: haz al menos un push que lo dispare para comprobar que quedó bien.

## Qué nunca se versiona

Esto es lo más importante del documento. Hay archivos que, subidos a un repositorio público, **exponen credenciales
reales**:

- `terraform/terraform.tfstate` — guarda **en claro** la contraseña del usuario de Cognito.
- `terraform/*.tfvars` — los valores propios de tu despliegue, contraseñas incluidas.
- `credenciales-lab.env` — las claves del Learner Lab, vivas mientras dura la sesión.
- `.terraform/`, `node_modules/`, `dist/`, `target/` — artefactos que se regeneran.

Todo eso ya viene cubierto por el `.gitignore` de la plantilla. Antes de tu primer push, verifica que `git status` no
los liste. Un `git add -A` descuidado los publica, y borrarlos después **no los quita del historial**: hay que rotar la
credencial.

## Convenciones

- **Rama de trabajo**: `main`, siempre (ver la sección **Rama de trabajo**).
- **Visibilidad**: público, o privado con el docente agregado como colaborador.
- **README.md propio**: qué hace tu solución, cómo levantarla en local y la URL desplegada.
- **Commits**: mensaje que diga qué cambió. `cambios`, `avance` o `.` no sirven para revisar.
