# =============================================================================
# El IDaaS: un user pool de Amazon Cognito
#
# Equivale al "tenant" de la actividad 1.2.3 y a la "app" de la 1.2.4, pero
# escrito en vez de clickeado. El user pool es el directorio de usuarios + el
# servidor de autorizacion (/authorize, /token, /userInfo, /jwks).
# =============================================================================

resource "aws_cognito_user_pool" "pool" {
  # El sufijo "-ng" existe para que este despliegue pueda convivir con el de la
  # version React (1.2.9) en la misma cuenta: el dominio del Hosted UI es unico
  # a nivel MUNDIAL, asi que dos pools no pueden pedir el mismo.
  name = "dsy1107-ng-${var.estudiante}"

  # El correo es el nombre de usuario. Es lo habitual en un CIAM: el cliente no
  # quiere inventar un username, quiere entrar con su correo.
  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]

  password_policy {
    minimum_length    = 8
    require_lowercase = true
    require_uppercase = true
    require_numbers   = true
    require_symbols   = false
  }

  # Solo un administrador crea usuarios. En un CIAM real (1.2.2) esto seria
  # false para permitir el auto-registro; aqui interesa que el pool tenga
  # exactamente el usuario que declaramos y ninguno mas.
  admin_create_user_config {
    allow_admin_create_user_only = true
  }

  account_recovery_setting {
    recovery_mechanism {
      name     = "verified_email"
      priority = 1
    }
  }

  # La version V2 del trigger de tokens solo existe desde el tier Essentials.
  # Es el default de AWS hoy, pero se fija explicito: si el default cambiara,
  # el sintoma seria un access token sin scopes y ningun mensaje de error.
  user_pool_tier = "ESSENTIALS"

  # El puente entre grupos y scopes. Ver lambda.tf y user-token-ms/README.md.
  lambda_config {
    pre_token_generation_config {
      lambda_arn = aws_lambda_function.user_token_ms.arn

      # V1_0 solo sabe agregar claims al ID token. Para tocar el claim scope
      # del ACCESS token hace falta V2_0, que es lo unico que lee el authorizer.
      lambda_version = "V2_0"
    }
  }

  # Nota de costos: el tier por defecto (Essentials) incluye 10.000 usuarios
  # activos al mes en la capa gratuita. Un curso completo no se acerca a eso.
}

# -----------------------------------------------------------------------------
# El dominio del Hosted UI: aqui viven /oauth2/authorize, /oauth2/token,
# /oauth2/userInfo y /logout. Es el "servidor de autorizacion" de la lamina 11
# de la presentacion 1.2.1.
# -----------------------------------------------------------------------------

locals {
  # Ver la nota de var.cognito_dominio en variables.tf.
  dominio_hosted_ui = var.cognito_dominio != "" ? var.cognito_dominio : "dsy1107-ng-${var.estudiante}"
}

resource "aws_cognito_user_pool_domain" "hosted_ui" {
  domain       = local.dominio_hosted_ui
  user_pool_id = aws_cognito_user_pool.pool.id

  # 1 = Hosted UI clasica, sin configuracion extra.
  # 2 = Managed Login, la pantalla nueva; exige definir un branding style o la
  #     pagina de login queda en blanco.
  managed_login_version = 1
}

# -----------------------------------------------------------------------------
# La aplicacion cliente: nuestro front en Angular.
#
# Es un CLIENTE PUBLICO (generate_secret = false): el codigo de una SPA se
# descarga completo en el navegador, asi que no puede guardar un secreto. Por
# eso el flujo es Authorization Code + PKCE (RFC 7636), donde el secreto lo
# reemplaza un code_verifier distinto en cada login.
# -----------------------------------------------------------------------------

resource "aws_cognito_user_pool_client" "spa" {
  name         = "spa-angular"
  user_pool_id = aws_cognito_user_pool.pool.id

  generate_secret = false

  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  supported_identity_providers         = ["COGNITO"]

  # Los permisos que el token puede llevar (los "scopes" de la lamina 13 de 1.2.1):
  #   openid  -> obligatorio en OIDC, habilita el ID Token
  #   email   -> agrega el correo a /oauth2/userInfo
  #   profile -> agrega nombre y demas atributos de perfil
  #   aws.cognito.signin.user.admin -> habilita la API GetUser de Cognito
  allowed_oauth_scopes = [
    "openid",
    "email",
    "profile",
    "aws.cognito.signin.user.admin",
  ]

  # A los origenes de desarrollo local se suma la URL de Amplify, que solo se
  # conoce despues de crear la app. Por eso se concatena aqui y no se escribe
  # a mano en las variables: un solo apply deja el login funcionando en
  # localhost y en el dominio publicado, sin copiar URLs entre pasos.
  #
  # La barra final es obligatoria: callback_urls se compara EXACTA contra el
  # redirect_uri que envia el front.
  callback_urls = concat(var.callback_urls, ["${local.url_amplify}/"])
  logout_urls   = concat(var.logout_urls, ["${local.url_amplify}/"])

  # Tokens cortos a proposito: que el 401 por expiracion se vea en clase.
  access_token_validity  = 60
  id_token_validity      = 60
  refresh_token_validity = 1

  token_validity_units {
    access_token  = "minutes"
    id_token      = "minutes"
    refresh_token = "days"
  }

  # El front nunca ve la contrasena: la escribe el usuario en el Hosted UI.
  # Por eso el unico flujo directo habilitado es el refresh.
  explicit_auth_flows = ["ALLOW_REFRESH_TOKEN_AUTH"]

  # No revela si un correo existe o no cuando el login falla.
  prevent_user_existence_errors = "ENABLED"
  enable_token_revocation       = true
}

# -----------------------------------------------------------------------------
# Un usuario de prueba, ya confirmado y con contrasena definitiva.
# Sin esto habria que crearlo a mano en la consola antes de cada demo.
# -----------------------------------------------------------------------------

resource "aws_cognito_user" "demo" {
  user_pool_id = aws_cognito_user_pool.pool.id
  username     = "test@duoc.cl"
  password     = "Duoc2026"

  attributes = {
    email          = "test@duoc.cl"
    email_verified = true
    name           = "oscar"
  }

  # No enviar correo de invitacion: el usuario es ficticio.
  message_action = "SUPPRESS"
}

# =============================================================================
# LOS PERMISOS: resource server + grupos
#
# Aqui esta la asimetria que confunde a todo el mundo la primera vez: en Cognito
# los scopes NO se asignan a usuarios, se asignan a CLIENTES. El access token
# lleva la interseccion entre allowed_oauth_scopes del cliente y lo que el front
# pidio en /authorize. Dos usuarios distintos por el mismo cliente reciben
# exactamente los mismos scopes.
#
# El permiso por persona vive en los GRUPOS. Y no hay nada nativo que conecte
# grupos con scopes: esa es la razon de existir de user-token-ms.
# =============================================================================

# Declara que scopes EXISTEN. El nombre real de cada uno es
# "<identifier>/<scope_name>", o sea "productos/read" y "productos/write".
resource "aws_cognito_resource_server" "productos" {
  user_pool_id = aws_cognito_user_pool.pool.id
  identifier   = "productos"
  name         = "API de productos"

  scope {
    scope_name        = "read"
    scope_description = "Consultar productos"
  }

  scope {
    scope_name        = "write"
    scope_description = "Crear, modificar y eliminar productos"
  }
}

# OJO CON LO QUE **NO** HAY AQUI, que es la decision de diseno de todo esto:
# estos dos scopes NO se agregan a allowed_oauth_scopes del cliente spa.
#
# Si estuvieran, cualquier usuario podria pedir productos/write en el
# /authorize y Cognito se lo daria, porque los scopes del cliente son iguales
# para todos. Al dejarlos fuera, el token base sale sin ellos y el unico capaz
# de ponerlos es el Lambda, que si mira quien eres. El front no puede
# concederse permisos a si mismo ni cambiando el codigo que corre en el
# navegador.

# -----------------------------------------------------------------------------
# Los grupos: aqui vive el permiso de cada persona.
# -----------------------------------------------------------------------------

resource "aws_cognito_user_group" "lectores" {
  user_pool_id = aws_cognito_user_pool.pool.id
  name         = "lectores"
  description  = "Puede consultar productos"
}

resource "aws_cognito_user_group" "editores" {
  user_pool_id = aws_cognito_user_pool.pool.id
  name         = "editores"
  description  = "Puede crear, modificar y eliminar productos"
}

# El usuario demo empieza pudiendo solo leer. La demostracion en clase consiste
# en agregarlo a "editores" desde la consola de Cognito -- sin tocar codigo ni
# Terraform -- y ver que el mismo POST que devolvia 403 pasa a devolver 201.
resource "aws_cognito_user_in_group" "demo_lector" {
  user_pool_id = aws_cognito_user_pool.pool.id
  username     = aws_cognito_user.demo.username
  group_name   = aws_cognito_user_group.lectores.name
}
