output "user_pool_id" {
  description = "Identificador del user pool."
  value       = aws_cognito_user_pool.pool.id
}

output "client_id" {
  description = "Client ID de la SPA. Es tambien la audiencia que valida el API Gateway."
  value       = aws_cognito_user_pool_client.spa.id
}

output "dominio_cognito" {
  description = "Dominio del Hosted UI: aqui viven /oauth2/authorize, /oauth2/token y /oauth2/userInfo."
  value       = "https://${aws_cognito_user_pool_domain.hosted_ui.domain}.auth.${var.aws_region}.amazoncognito.com"
}

output "issuer" {
  description = "Emisor de los tokens. El API Gateway solo confia en tokens firmados por este issuer."
  value       = "https://${aws_cognito_user_pool.pool.endpoint}"
}

output "discovery_url" {
  description = "Documento de descubrimiento OIDC. Abrelo en el navegador: ahi estan declarados todos los endpoints (es el bloque 1 de la guia 1.2.2b)."
  value       = "https://${aws_cognito_user_pool.pool.endpoint}/.well-known/openid-configuration"
}

output "jwks_url" {
  description = "Claves publicas con las que el API Gateway verifica la firma de cada token."
  value       = "https://${aws_cognito_user_pool.pool.endpoint}/.well-known/jwks.json"
}

output "url_datos_protegido" {
  description = "Ruta protegida. Sin Authorization: Bearer <access_token> responde 401."
  value       = "${aws_apigatewayv2_api.api.api_endpoint}/datos"
}

output "url_datos_publico" {
  description = "La misma integracion sin authorizer. Sirve para comparar."
  value       = "${aws_apigatewayv2_api.api.api_endpoint}/publico/datos"
}

output "url_userinfo" {
  description = "Endpoint /userInfo de OIDC: la identidad del usuario segun el IDaaS."
  value       = "https://${aws_cognito_user_pool_domain.hosted_ui.domain}.auth.${var.aws_region}.amazoncognito.com/oauth2/userInfo"
}

output "usuario_demo" {
  description = "Con este correo se inicia sesion en el Hosted UI."
  value       = aws_cognito_user.demo.username
}

# -----------------------------------------------------------------------------
# La configuracion del front, en JSON.
#
#     terraform output -raw config_frontend > ../frontend/public/config.json
#
# Angular no lee variables de entorno en el navegador (no hay import.meta.env
# como en Vite): lo que llega al cliente es un archivo estatico. Por eso la app
# hace fetch de este config.json al arrancar. La ventaja didactica es que se ve
# la configuracion con los ojos, y que cambiarla no obliga a recompilar.
#
# Nada de esto es secreto: el client_id de una SPA es publico por definicion, y
# lo que protege el flujo es PKCE.
# -----------------------------------------------------------------------------

output "config_frontend" {
  description = "Contenido listo para frontend/public/config.json"
  value       = <<-EOT
    {
      "region": "${var.aws_region}",
      "cognitoDomain": "https://${aws_cognito_user_pool_domain.hosted_ui.domain}.auth.${var.aws_region}.amazoncognito.com",
      "clientId": "${aws_cognito_user_pool_client.spa.id}",
      "redirectUri": "${var.callback_urls[0]}",
      "apiUrl": "${aws_apigatewayv2_api.api.api_endpoint}"
    }
  EOT
}

# El gemelo de config_frontend para el bundle que se sube a Amplify. La unica
# diferencia es redirectUri: alla el front vive en el dominio de Amplify, no en
# localhost. Subir el build con el config de localhost es el fallo numero uno
# del despliegue: Cognito corta en /authorize con redirect_mismatch.
output "config_frontend_hosted" {
  description = "Contenido de public/config.json para el build que se publica en Amplify"
  value       = <<-EOT
    {
      "region": "${var.aws_region}",
      "cognitoDomain": "https://${aws_cognito_user_pool_domain.hosted_ui.domain}.auth.${var.aws_region}.amazoncognito.com",
      "clientId": "${aws_cognito_user_pool_client.spa.id}",
      "redirectUri": "${local.url_amplify}/",
      "apiUrl": "${aws_apigatewayv2_api.api.api_endpoint}"
    }
  EOT
}

output "probar_sin_token" {
  description = "Debe responder 401. Es la mitad del ejercicio."
  value       = "curl -s -o /dev/null -w 'HTTP %%{http_code}\\n' ${aws_apigatewayv2_api.api.api_endpoint}/datos"
}

# -----------------------------------------------------------------------------
# La guia 1.2.9b construye el front en React con Vite, que lee variables
# FE_* desde .env.local en tiempo de build. El prefijo no es libre: Vite solo
# expone al navegador el que se declare en envPrefix, y la guia lo fija en
# 'FE_'. Si se cambia aqui, hay que cambiarlo alli o import.meta.env llega
# vacio. Este output entrega ese archivo listo,
# igual que config_frontend lo hace para Angular: un mismo despliegue sirve a
# las dos guias que se entregan a los alumnos.
#
# El redirect_uri de React es el del 5173; el de Angular, el del 4200. Se elige
# de var.callback_urls, que autoriza los dos.
# -----------------------------------------------------------------------------

locals {
  callbacks_react = [for u in var.callback_urls : u if can(regex(":5173", u))]
  redirect_react  = length(local.callbacks_react) > 0 ? local.callbacks_react[0] : var.callback_urls[0]
}

output "env_frontend" {
  description = "Contenido listo para frontend/.env.local del front en React (guia 1.2.9)"
  value       = <<-EOT
    FE_AWS_REGION=${var.aws_region}
    FE_COGNITO_DOMAIN=https://${aws_cognito_user_pool_domain.hosted_ui.domain}.auth.${var.aws_region}.amazoncognito.com
    FE_COGNITO_CLIENT_ID=${aws_cognito_user_pool_client.spa.id}
    FE_REDIRECT_URI=${local.redirect_react}
    FE_API_URL=${aws_apigatewayv2_api.api.api_endpoint}
  EOT
}
