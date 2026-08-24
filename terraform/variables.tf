variable "estudiante" {
  description = "Tu apellido en minusculas. Hace unicos el user pool, el dominio y la API."
  type        = string
  default     = "rivera"
  validation {
    condition     = can(regex("^[a-z0-9-]{3,20}$", var.estudiante))
    error_message = "Solo minusculas, numeros y guiones, entre 3 y 20 caracteres. El dominio de Cognito es global: 'perez' sirve, 'Perez' no."
  }
}

variable "seccion" {
  description = "Seccion del curso."
  type        = string
  default     = "001V"
}

variable "aws_region" {
  description = "Region donde se despliega todo. El issuer del token depende de esta region."
  type        = string
  default     = "us-east-1"
}

variable "usuario_demo_email" {
  description = "Correo del usuario de prueba que se crea dentro del user pool. Es tambien su nombre de usuario."
  type        = string
  default     = "test@duoc.cl"
  validation {
    condition     = can(regex("^[^@]+@[^@]+\\.[^@]+$", var.usuario_demo_email))
    error_message = "Debe ser un correo valido."
  }
}

variable "usuario_demo_nombre" {
  description = "Nombre a mostrar del usuario de prueba. Aparece en el claim 'name' y en /oauth2/userInfo."
  type        = string
  default     = "Estudiante Duoc Demo"
}

variable "usuario_demo_password" {
  description = "Contrasena permanente del usuario de prueba. Minimo 8 caracteres, con mayuscula, minuscula y numero."
  type        = string
  sensitive   = true
  default     = "Duoc2026"

  validation {
    condition = (
      length(var.usuario_demo_password) >= 6 &&
      can(regex("[a-z]", var.usuario_demo_password)) &&
      can(regex("[A-Z]", var.usuario_demo_password)) &&
      can(regex("[0-9]", var.usuario_demo_password))
    )
    error_message = "Minimo 8 caracteres, con al menos una minuscula, una mayuscula y un numero (es la politica del user pool de mas abajo)."
  }
}

# -----------------------------------------------------------------------------
# Los tres valores que dependen del puerto del front.
#
# "ng serve" levanta en el 4200 y Vite (React) en el 5173. Los defaults
# autorizan AMBOS, porque las guias 1.2.9b (React) y 1.2.9d (Angular) se
# entregan las dos y comparten este mismo despliegue. Si cambias un puerto,
# cambia las tres variables a la vez o el login falla con redirect_mismatch.
# -----------------------------------------------------------------------------

variable "origenes_frontend" {
  description = "Origenes autorizados por CORS en el API Gateway: ng serve (4200) y Vite (5173)."
  type        = list(string)
  default     = ["http://localhost:4200", "http://localhost:5173"]
}

variable "callback_urls" {
  description = "URLs a las que el Hosted UI puede devolver el authorization code. Debe coincidir EXACTA con redirect_uri del front."
  type        = list(string)
  default     = ["http://localhost:4200/", "http://localhost:5173/"]
}

variable "logout_urls" {
  description = "URLs a las que Cognito puede volver despues del logout."
  type        = list(string)
  default     = ["http://localhost:4200/", "http://localhost:5173/"]
}

variable "backend_url" {
  description = "La API publica que queda detras del API Manager. Es la misma de la actividad 1.1.2."
  type        = string
  default     = "https://mindicador.cl/api"
}
