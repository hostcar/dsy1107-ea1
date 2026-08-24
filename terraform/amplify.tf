# =============================================================================
# Amplify Hosting: el front deja de vivir en localhost.
#
# Hasta aqui la app solo corria con "ng serve" en la maquina del alumno. Esto
# publica el bundle compilado en un dominio HTTPS real, que es la condicion
# para que el login de Cognito se vea como se ve en produccion.
#
# Este archivo se aplica SOLO. Todavia no toca Cognito ni el API Gateway: el
# objetivo del paso es obtener la URL, que es el dato que los dos necesitan y
# que hoy no existe. Ejecuta "terraform plan" antes del apply para ver que se
# agregan exactamente dos recursos y no se modifica nada de lo ya desplegado.
#
# No se declara "repository": sin repositorio conectado, Amplify acepta
# despliegues manuales (se sube un .zip con el build). Es lo que corresponde
# aqui, porque el curso no usa control de versiones y porque public/config.json
# esta en .gitignore: viajando dentro del zip, el problema desaparece.
# =============================================================================

resource "aws_amplify_app" "front" {
  name = "dsy1107-${var.estudiante}"

  # WEB = sitio estatico. El build de Angular son archivos, no un servidor.
  platform = "WEB"

  # Una SPA sirve index.html para cualquier ruta; sin esto, todo lo que no sea
  # "/" responde 404. La lista de extensiones excluye json a proposito: si no,
  # esta misma regla se tragaria /config.json y la app arrancaria sin
  # configuracion.
  custom_rule {
    source = "</^[^.]+$|\\.(?!(css|gif|ico|jpg|js|png|txt|svg|woff|woff2|ttf|map|json|webp)$)([^.]+$)/>"
    target = "/index.html"
    status = "200"
  }
}

resource "aws_amplify_branch" "main" {
  app_id      = aws_amplify_app.front.id
  branch_name = "main"
  framework   = "Angular"
  stage       = "PRODUCTION"
}

# El dominio por defecto es "<app_id>.amplifyapp.com" y el app_id lo asigna AWS,
# asi que la URL es unica por alumno sin necesidad de inventar nombres (a
# diferencia del dominio de Cognito, que si es global y por eso lleva apellido).
locals {
  url_amplify = "https://${aws_amplify_branch.main.branch_name}.${aws_amplify_app.front.default_domain}"
}

output "amplify_app_id" {
  description = "ID de la app. Lo necesita 'aws amplify create-deployment' para subir el zip."
  value       = aws_amplify_app.front.id
}

output "amplify_url" {
  description = "URL publica del front. Va en callback_urls, logout_urls y CORS (paso siguiente)."
  value       = local.url_amplify
}
