# =============================================================================
# El API Manager de la actividad 1.1.2, ahora con la puerta cerrada.
#
# Respecto de "codigo/1.1.2 api gateway/main.tf" se agregan tres cosas:
#   1. cors_configuration   -> lo de la actividad 1.1.4, ahora si hace falta
#                              porque quien llama es un navegador
#   2. aws_apigatewayv2_authorizer -> el JWT authorizer contra Cognito
#   3. una ruta publica gemela      -> para comparar 200 contra 401 en vivo
# =============================================================================

resource "aws_apigatewayv2_api" "api" {
  name          = "api-mindicador-ng-${var.estudiante}"
  protocol_type = "HTTP"
  description   = "DSY1107 1.2.9 (Angular) - mindicador.cl protegido con Cognito"

  # Sin esto el navegador bloquea la respuesta por Same-Origin Policy, aunque
  # la API responda 200. Postman no lo nota; el front si.
  cors_configuration {
    # El origen de Amplify va SIN barra final: un header Origin nunca la lleva.
    # Es el error espejo del de callback_urls, que si la exige.
    allow_origins = concat(var.origenes_frontend, [local.url_amplify])
    allow_methods = ["GET", "OPTIONS"]

    # "authorization" es el header que obliga al preflight OPTIONS. Si falta
    # aqui, el navegador cancela la peticion antes de enviarla.
    allow_headers = ["authorization", "content-type"]
    max_age       = 300
  }
}

# La integracion no cambia respecto de 1.1.2: HTTP_PROXY contra una API publica.
resource "aws_apigatewayv2_integration" "backend" {
  api_id                 = aws_apigatewayv2_api.api.id
  integration_type       = "HTTP_PROXY"
  integration_method     = "GET"
  integration_uri        = var.backend_url
  payload_format_version = "1.0"
  timeout_milliseconds   = 29000

  # Quien manda sobre integration_uri es publicar-ecs.sh, no este archivo.
  # Sin balanceador, la IP de la task cambia en cada despliegue, y el script la
  # reapunta al terminar. Sin este ignore_changes, el siguiente "terraform
  # apply" la devolveria a var.backend_url (mindicador.cl) y el backend propio
  # quedaria desconectado sin que nadie lo note.
  #
  # Es el mismo tipo de deriva que teniamos con SERVER_PORT en Beanstalk, pero
  # aqui es deliberada: el valor es dinamico por naturaleza y Terraform no puede
  # conocerlo.
  lifecycle {
    ignore_changes = [integration_uri]
  }
}

# -----------------------------------------------------------------------------
# El JWT Authorizer: el API Gateway valida el token por su cuenta.
#
# No llama a Cognito en cada peticion: descarga una vez las claves publicas de
# {issuer}/.well-known/jwks.json (el endpoint /jwks de la lamina 14 de 1.2.1) y
# con ellas verifica la firma, la expiracion, el issuer y la audiencia.
# -----------------------------------------------------------------------------

resource "aws_apigatewayv2_authorizer" "cognito" {
  api_id           = aws_apigatewayv2_api.api.id
  name             = "cognito-jwt"
  authorizer_type  = "JWT"
  identity_sources = ["$request.header.Authorization"]

  jwt_configuration {
    # El access token de Cognito no trae claim "aud" sino "client_id".
    # API Gateway compara contra este valor de todas formas.
    audience = [aws_cognito_user_pool_client.spa.id]

    # .endpoint devuelve "cognito-idp.us-east-1.amazonaws.com/us-east-1_XXXX",
    # sin esquema. El issuer del token lo lleva, por eso se antepone https://.
    issuer = "https://${aws_cognito_user_pool.pool.endpoint}"
  }
}

# Ruta protegida: sin token valido, 401. Nunca llega a mindicador.cl.
resource "aws_apigatewayv2_route" "datos_privado" {
  api_id    = aws_apigatewayv2_api.api.id
  route_key = "GET /datos"
  target    = "integrations/${aws_apigatewayv2_integration.backend.id}"

  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.cognito.id

  # Ademas de un token valido, exige que ese token traiga este scope.
  # Es la diferencia entre "quien eres" y "que puedes hacer" (1.2.1).
  authorization_scopes = ["openid"]
}

# La misma integracion sin authorizer, solo para contrastar en clase.
# En un sistema real esta ruta no existiria.
resource "aws_apigatewayv2_route" "datos_publico" {
  api_id    = aws_apigatewayv2_api.api.id
  route_key = "GET /publico/datos"
  target    = "integrations/${aws_apigatewayv2_integration.backend.id}"
  # authorization_type = "NONE" es el valor por defecto.
}

# =============================================================================
# El CRUD de /productos.
#
# POR QUE NO REUTILIZA LA INTEGRACION DE ARRIBA, que es la pregunta obvia:
# aquella tiene integration_method = "GET" y una URI fija que termina en /datos.
# Una integracion HTTP_PROXY asi no reenvia ni el metodo ni la ruta de quien
# llamo -- los reemplaza por los suyos. Un POST /productos que entrara por ahi
# llegaria al backend convertido en GET /datos, y devolveria indicadores
# economicos con un 200, sin que nada parezca roto. De todos los fallos
# posibles, ese es el peor: silencioso y con cara de exito.
#
# De ahi que hagan falta dos integraciones y no una: la URI no admite un patron
# que sirva a la vez para la coleccion y para un elemento.
#
#   ANY /productos            -> http://IP:8080/productos
#   ANY /productos/{proxy+}   -> http://IP:8080/productos/{proxy}
#
# {proxy+} es una ruta glotona: captura /productos/7 y tambien cualquier cosa
# que cuelgue mas abajo. La variable {proxy} del lado de la integracion es la
# que arrastra ese trozo hasta el backend.
# =============================================================================

resource "aws_apigatewayv2_integration" "productos_coleccion" {
  api_id           = aws_apigatewayv2_api.api.id
  integration_type = "HTTP_PROXY"

  # ANY y no GET: es lo que deja pasar el metodo original. Con el CRUD esto
  # importa mas que en /datos, porque aqui el metodo ES la operacion.
  integration_method     = "ANY"
  integration_uri        = "${var.backend_url}/productos"
  payload_format_version = "1.0"
  timeout_milliseconds   = 29000

  # Mismo motivo que en la integracion de /datos: la IP de la task cambia en
  # cada despliegue y quien la escribe es publicar-ecs.sh, no este archivo.
  lifecycle {
    ignore_changes = [integration_uri]
  }
}

resource "aws_apigatewayv2_integration" "productos_elemento" {
  api_id                 = aws_apigatewayv2_api.api.id
  integration_type       = "HTTP_PROXY"
  integration_method     = "ANY"
  integration_uri        = "${var.backend_url}/productos/{proxy}"
  payload_format_version = "1.0"
  timeout_milliseconds   = 29000

  lifecycle {
    ignore_changes = [integration_uri]
  }
}

# Las dos rutas van detras del mismo authorizer que /datos: la puerta es una
# sola, y agregar recursos no agrega maneras de entrar.
resource "aws_apigatewayv2_route" "productos_coleccion" {
  api_id    = aws_apigatewayv2_api.api.id
  route_key = "ANY /productos"
  target    = "integrations/${aws_apigatewayv2_integration.productos_coleccion.id}"

  authorization_type   = "JWT"
  authorizer_id        = aws_apigatewayv2_authorizer.cognito.id
  authorization_scopes = ["openid"]
}

resource "aws_apigatewayv2_route" "productos_elemento" {
  api_id    = aws_apigatewayv2_api.api.id
  route_key = "ANY /productos/{proxy+}"
  target    = "integrations/${aws_apigatewayv2_integration.productos_elemento.id}"

  authorization_type   = "JWT"
  authorizer_id        = aws_apigatewayv2_authorizer.cognito.id
  authorization_scopes = ["openid"]
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.api.id
  name        = "$default"
  auto_deploy = true
  description = "Stage por defecto - URL sin prefijo de etapa"
}
