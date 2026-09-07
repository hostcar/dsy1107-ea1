terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source = "hashicorp/aws"
      # 5.83+ es el minimo real: antes de esa version no existe el argumento
      # managed_login_version del dominio de Cognito.
      version = "~> 5.100"
    }

    # Arma el zip del Lambda desde user-token-ms/ sin pasar por un script.
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.7"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Asignatura = "DSY1107"
      Seccion    = var.seccion
      Estudiante = var.estudiante
      Actividad  = "1.2.9-angular"
      Origen     = "terraform"
    }
  }
}
