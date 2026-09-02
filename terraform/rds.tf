# =============================================================================
# Una base de datos PostgreSQL administrada (Amazon RDS).
#
# Es el mismo salto conceptual que hubo con el backend: dejar de administrar la
# maquina y quedarse solo con el servicio. Aqui no hay un servidor Postgres que
# instalar, parchar ni respaldar; hay un endpoint, un usuario y una contrasena.
#
# QUEDA APARTE DEL BACKEND A PROPOSITO. El backend de la EA1 es un proxy con
# cache en memoria sobre mindicador.cl y NO necesita base de datos: si le
# agregas el driver de Postgres y no le das una URL, Spring Boot no arranca.
# Este archivo entrega la base y las variables listas; conectarla es un paso
# explicito, documentado abajo en el output "backend_env".
#
#   terraform apply -target=aws_db_instance.postgres
#
# Tarda entre 5 y 10 minutos. Es normal: RDS aprovisiona almacenamiento, crea
# la instancia y corre el arranque inicial del motor. No lo interrumpas.
# =============================================================================

# -----------------------------------------------------------------------------
# Lo que el Learner Lab permite, y lo que no.
#
#   - Tipos de instancia acotados. db.t3.micro pasa. Si pruebas uno mayor y
#     responde AccessDenied con un deny explicito, no es tu politica: es el
#     limite del lab.
#   - No hay roles IAM que crear, asi que quedan fuera Enhanced Monitoring y
#     Performance Insights (los dos exigen un rol propio). Estan apagados abajo
#     de forma explicita para que se vea que es una decision, no un olvido.
#   - La cuenta rota entre sesiones: esta base y sus datos DESAPARECEN al
#     cerrar el lab. Todo lo que hay aqui asume eso (sin respaldos, sin
#     snapshot final, sin proteccion de borrado). No guardes nada que duela
#     perder.
# -----------------------------------------------------------------------------

variable "db_nombre" {
  description = "Nombre de la base que se crea dentro de la instancia. Sin guiones: es un identificador de PostgreSQL, no un nombre de recurso AWS."
  type        = string
  default     = "dsy1107"

  validation {
    condition     = can(regex("^[a-zA-Z][a-zA-Z0-9_]{0,62}$", var.db_nombre))
    error_message = "Debe empezar con letra y llevar solo letras, numeros y guion bajo."
  }
}

variable "db_usuario" {
  description = "Usuario maestro. Es el dueno de la base y no se puede renombrar despues sin recrear la instancia."
  type        = string
  default     = "postgres"

  validation {
    condition     = can(regex("^[a-zA-Z][a-zA-Z0-9_]{0,62}$", var.db_usuario)) && var.db_usuario != "rdsadmin"
    error_message = "Letras, numeros y guion bajo, empezando con letra. 'rdsadmin' esta reservado por RDS."
  }
}

variable "db_password" {
  description = "Contrasena del usuario maestro. Minimo 8 caracteres; RDS rechaza '/', '@', '\"' y el espacio."
  type        = string
  sensitive   = true
  default     = "Duoc2026Postgres"

  validation {
    condition = (
      length(var.db_password) >= 8 &&
      !can(regex("[/@\" ]", var.db_password))
    )
    error_message = "Minimo 8 caracteres y sin '/', '@', comillas dobles ni espacios (los prohibe RDS, no Terraform)."
  }
}

variable "db_instancia" {
  description = "Tipo de instancia. El Learner Lab solo autoriza clases chicas."
  type        = string
  default     = "db.t3.micro"
}

variable "db_almacenamiento_gb" {
  description = "Disco en GB. 20 es el minimo de gp3 y sobra para la asignatura."
  type        = number
  default     = 20
}

variable "db_version" {
  description = "Version mayor de PostgreSQL. Se declara solo la mayor: AWS elige la ultima menor disponible."
  type        = string
  default     = "17"
}

# -----------------------------------------------------------------------------
# Desde donde se puede abrir una conexion al 5432.
#
# El default esta abierto a internet, y hay que decirlo en voz alta: cualquiera
# que averigue el endpoint puede intentar autenticarse contra tu base, y lo
# unico que lo detiene es la contrasena. Se deja asi porque en clase la IP de
# cada estudiante cambia (casa, campus, celular) y porque el objetivo es que
# DBeaver o pgAdmin conecten a la primera.
#
# Si quieres cerrarlo de verdad, averigua tu IP y pasala:
#
#   terraform apply -var='origenes_postgres=["'"$(curl -s ifconfig.me)"'/32"]'
# -----------------------------------------------------------------------------
variable "origenes_postgres" {
  description = "CIDRs autorizados a conectar al 5432."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

# -----------------------------------------------------------------------------
# La red.
#
# Se reutilizan tal cual la VPC y las subredes que ya declara ecs.tf --el
# archivo donde vive el servicio ECS del backend-- (data.aws_vpc.default y
# data.aws_subnets.publicas): son data sources, no recursos, y volver a
# declararlas aqui seria un error de nombre duplicado.
#
# Tambien depende de aws_route.salida_a_internet, el que le agrega la ruta
# 0.0.0.0/0 a la VPC por defecto de este lab. Sin esa ruta la instancia se crea
# igual, pero es inalcanzable desde fuera y el sintoma es un timeout mudo del
# cliente, sin ningun error en la consola de AWS.
#
# El grupo de subredes necesita AL MENOS DOS zonas de disponibilidad, aunque la
# instancia viva en una sola. Es un requisito de RDS: reserva de antemano el
# lugar al que podria moverse. La VPC por defecto trae una subred por AZ, asi
# que la lista alcanza sin hacer nada.
# -----------------------------------------------------------------------------
resource "aws_db_subnet_group" "postgres" {
  name        = "dsy1107-db-${var.estudiante}"
  description = "Subredes publicas de la VPC por defecto, las mismas que usan las tasks de ECS"
  subnet_ids  = data.aws_subnets.publicas.ids
}

resource "aws_security_group" "postgres" {
  name        = "dsy1107-db-${var.estudiante}"
  description = "PostgreSQL: 5432 desde el backend en ECS y desde el cliente del estudiante"
  vpc_id      = data.aws_vpc.default.id

  # El backend. Aunque la base sea publica, cuando la task resuelve el nombre
  # DNS del endpoint desde dentro de la VPC obtiene la IP PRIVADA, y el trafico
  # nunca sale a internet. Por eso el origen es el grupo de seguridad de la
  # task y no su IP publica, que ademas cambia en cada despliegue.
  #
  # Esto vale mientras el servicio ECS mantenga network_mode = "awsvpc", que es
  # lo que le da a cada task su propia interfaz de red y, con ella, su propio
  # grupo de seguridad. Si algun dia pasara a "bridge" o "host", la task
  # heredaria la interfaz de la maquina que la hospeda y habria que cambiar
  # este origen por el grupo de seguridad de esas maquinas. El sintoma de no
  # hacerlo es el peor de todos: la conexion se queda esperando hasta expirar,
  # sin un solo error que diga que falta una regla.
  ingress {
    description     = "5432 desde la task de ECS, por dentro de la VPC"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.tarea.id]
  }

  # El cliente de escritorio (DBeaver, pgAdmin, psql). Ver el comentario de
  # var.origenes_postgres: esto es lo que hay que cerrar si te preocupa.
  ingress {
    description = "5432 desde los origenes autorizados"
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = var.origenes_postgres
  }

  egress {
    description = "Salida sin restriccion"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# -----------------------------------------------------------------------------
# La instancia.
# -----------------------------------------------------------------------------
resource "aws_db_instance" "postgres" {
  identifier = "dsy1107-db-${var.estudiante}"

  engine         = "postgres"
  engine_version = var.db_version
  instance_class = var.db_instancia

  db_name  = var.db_nombre
  username = var.db_usuario
  password = var.db_password
  port     = 5432

  # gp3 en vez de gp2: mismo precio o menos, y rendimiento base garantizado sin
  # depender del tamano del disco. 20 GB es el minimo del tipo.
  storage_type          = "gp3"
  allocated_storage     = var.db_almacenamiento_gb
  max_allocated_storage = 0 # sin autoescalado: que no crezca sola y sorprenda la factura
  storage_encrypted     = true

  db_subnet_group_name   = aws_db_subnet_group.postgres.name
  vpc_security_group_ids = [aws_security_group.postgres.id]

  # PUBLICA A PROPOSITO. En produccion la base vive en subredes privadas y solo
  # la alcanza la aplicacion. Aqui se necesita abrirla porque parte del
  # ejercicio es conectarse con un cliente de escritorio y VER las tablas, y en
  # el lab no hay VPN ni bastion. Quien decide quien entra es el grupo de
  # seguridad de mas arriba.
  publicly_accessible = true

  multi_az = false # una sola AZ: es lo unico que cabe en el lab, y no hay nada que sobreviva igual

  # Sin respaldos ni snapshot final. La cuenta del lab se borra completa entre
  # sesiones, asi que un respaldo dentro de esa misma cuenta no protege de nada
  # y un snapshot final solo hace que "terraform destroy" tarde mas.
  backup_retention_period  = 0
  skip_final_snapshot      = true
  delete_automated_backups = true
  deletion_protection      = false

  # Los dos exigen un rol IAM propio, y el Learner Lab no deja crear roles.
  # Apagados explicitamente para que el que lea sepa por que no estan.
  monitoring_interval          = 0
  performance_insights_enabled = false

  # Los cambios se aplican al momento, no en la ventana de mantencion. En una
  # clase nadie va a esperar hasta el domingo a las 3 de la manana.
  apply_immediately          = true
  auto_minor_version_upgrade = true

  # SIN exportar los logs del motor a CloudWatch, a proposito: esa opcion crea
  # un log group aparte y aqui no hace falta ninguno. Los dos fallos que de
  # verdad va a ver un estudiante --contrasena mala y falta de sslmode-- los
  # dice el cliente en su propia pantalla.
  #
  # Si algun dia hiciera falta mirar por que el motor rechaza una conexion, se
  # enciende con:  enabled_cloudwatch_logs_exports = ["postgresql"]
}

# -----------------------------------------------------------------------------
# Salidas
# -----------------------------------------------------------------------------

output "db_endpoint" {
  description = "Host y puerto de la base. Es lo que se pega en DBeaver o pgAdmin."
  value       = aws_db_instance.postgres.endpoint
}

output "db_host" {
  description = "Solo el host, sin el puerto."
  value       = aws_db_instance.postgres.address
}

output "db_nombre" {
  description = "Base que se creo dentro de la instancia."
  value       = aws_db_instance.postgres.db_name
}

output "db_usuario" {
  description = "Usuario maestro."
  value       = aws_db_instance.postgres.username
}

# -----------------------------------------------------------------------------
# Conectarse.
#
# El sslmode=require NO es decorativo. Desde PostgreSQL 15, RDS trae el
# parametro rds.force_ssl en 1: el servidor rechaza toda conexion en claro. El
# error del cliente es "no pg_hba.conf entry for host ... no encryption", que
# suena a permisos y es cifrado. Si aparece, falta esto.
# -----------------------------------------------------------------------------

output "db_psql" {
  description = "Comando para conectarse desde la terminal."
  value       = "psql 'postgresql://${aws_db_instance.postgres.username}@${aws_db_instance.postgres.endpoint}/${aws_db_instance.postgres.db_name}?sslmode=require'"
}

output "db_jdbc_url" {
  description = "URL JDBC para Spring Boot."
  value       = "jdbc:postgresql://${aws_db_instance.postgres.endpoint}/${aws_db_instance.postgres.db_name}?sslmode=require"
}

# -----------------------------------------------------------------------------
# Como configurarla al backend
#
# El backend de la EA1 no la usa. Para que la use:
#
#   1. Agregar al pom.xml spring-boot-starter-data-jpa y el driver
#      org.postgresql:postgresql.
#   2. Copiar estas tres variables al bloque "environment" del contenedor en
#      ecs.tf. Ojo: ahi quedan a la vista en la definicion de task, que
#      cualquiera con acceso a la consola puede leer. Lo correcto seria
#      "secrets" apuntando a Secrets Manager; se deja en environment porque es
#      un ejercicio de clase y porque Secrets Manager agrega un recurso y un
#      permiso mas que discutir.
#   3. Volver a desplegar con scripts/publicar-ecs.sh.
#
#   terraform output -raw backend_env
# -----------------------------------------------------------------------------
output "backend_env" {
  description = "Variables de entorno del datasource, listas para el contenedor del backend."
  sensitive   = true
  value       = <<-EOT
    SPRING_DATASOURCE_URL=jdbc:postgresql://${aws_db_instance.postgres.endpoint}/${aws_db_instance.postgres.db_name}?sslmode=require
    SPRING_DATASOURCE_USERNAME=${aws_db_instance.postgres.username}
    SPRING_DATASOURCE_PASSWORD=${var.db_password}
  EOT
}
