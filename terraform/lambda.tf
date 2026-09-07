# =============================================================================
# El Lambda que traduce grupos a scopes (user-token-ms/)
#
# Cognito no conecta por si solo "el usuario pertenece al grupo editores" con
# "el token lleva el scope productos/write". Este trigger es esa conexion, y es
# la unica manera de que la autorizacion dependa del usuario y no del cliente.
#
# El equivalente en Firebase son las blocking functions (beforeUserSignedIn):
# codigo propio que el IDaaS ejecuta dentro del flujo de login. La diferencia
# es que alli el "scope" es un claim inventado que solo tu backend entiende;
# aqui lo lee el API Gateway sin que escribas una linea de autorizacion.
# =============================================================================

# El zip se arma en cada plan a partir del fuente. output_base64sha256 hace que
# editar index.mjs baste para que el siguiente apply redespliegue: sin eso,
# Terraform no ve el cambio y el Lambda se queda con el codigo viejo.
data "archive_file" "user_token_ms" {
  type        = "zip"
  source_file = "${path.module}/../user-token-ms/index.mjs"
  output_path = "${path.module}/user-token-ms.zip"
}

resource "aws_lambda_function" "user_token_ms" {
  function_name = "user-token-ms-${var.estudiante}"
  description   = "Pre token generation V2: agrega al access token los scopes del grupo del usuario"

  # Mismo motivo que en ecs.tf: el Learner Lab no deja crear roles IAM.
  # LabRole ya trae permisos de escritura en CloudWatch, que es todo lo que
  # este Lambda necesita -- no llama a ninguna API de AWS.
  role = "arn:aws:iam::${data.aws_caller_identity.actual.account_id}:role/LabRole"

  runtime = "nodejs22.x"
  handler = "index.handler"

  filename         = data.archive_file.user_token_ms.output_path
  source_code_hash = data.archive_file.user_token_ms.output_base64sha256

  # Corre dentro del login: si tarda, el usuario espera. Cinco segundos es
  # holgado para un flatMap sobre una lista de grupos.
  timeout     = 5
  memory_size = 128
}

# Sin este permiso el Lambda existe, el trigger esta configurado, y el login
# falla con un error generico de Cognito que no menciona ni permisos ni Lambda.
resource "aws_lambda_permission" "cognito" {
  statement_id  = "AllowCognitoInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.user_token_ms.function_name
  principal     = "cognito-idp.amazonaws.com"
  source_arn    = aws_cognito_user_pool.pool.arn
}

# Declarado a proposito y no dejado a que Lambda lo cree solo: asi tiene
# retencion y no se acumula. Aqui se ve, login a login, que grupos trajo el
# usuario y que scopes se le concedieron.
resource "aws_cloudwatch_log_group" "user_token_ms" {
  name              = "/aws/lambda/${aws_lambda_function.user_token_ms.function_name}"
  retention_in_days = 7
}

output "lambda_user_token_ms" {
  description = "Nombre de la funcion. Va en duro en user_token_ms_deploy.yml; si cambia var.estudiante, hay que actualizarlo alli."
  value       = aws_lambda_function.user_token_ms.function_name
}

output "lambda_user_token_ms_logs" {
  description = "Que grupos trajo cada login y que scopes se concedieron."
  value       = "aws logs tail ${aws_cloudwatch_log_group.user_token_ms.name} --follow"
}
