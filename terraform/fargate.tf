# =============================================================================
# El backend en ECS Fargate.
#
# Reemplaza a beanstalk.tf, que en esta cuenta no se puede usar: tres intentos,
# tres LaunchWaitCondition agotados a los ~18 minutos, con dos plataformas y dos
# tipos de instancia distintos. App Runner, la alternativa mas parecida a
# Beanstalk, esta denegada en el Learner Lab (apprunner:CreateService).
#
# La diferencia de fondo: en Beanstalk entregabas un jar y AWS decidia como
# correrlo; aqui entregas una imagen y declaras tu como corre. Mas piezas a la
# vista, pero ninguna que pueda quedarse en CREATE_FAILED.
#
# SIN BALANCEADOR, a proposito. Un ALB daria una direccion estable, pero cuesta
# por hora y agrega cuatro recursos a un ejercicio de clase. En su lugar la task
# sale con IP publica y publicar-fargate.sh reapunta el API Gateway despues de
# cada despliegue. El precio de esa decision esta abajo, en el grupo de
# seguridad, y hay que decirlo en voz alta.
#
# ORDEN DE USO (la imagen tiene que existir antes de que arranque la task):
#
#   1. terraform apply -target=aws_ecr_repository.backend
#   2. scripts/publicar-fargate.sh
#   3. terraform apply
#
# Aplicar todo de una tambien funciona: el servicio queda reintentando hasta que
# haya imagen. Es el equivalente al primer 502 de Beanstalk, y no es un error.
# =============================================================================

# -----------------------------------------------------------------------------
# La red: se reutiliza la VPC por defecto.
#
# Crear una VPC propia seria lo correcto en produccion y una distraccion aqui:
# la EA1 trata de API Manager e identidad, no de redes. Las subredes publicas
# alcanzan porque la task necesita salida a internet para bajar la imagen de ECR
# y para llamar a mindicador.cl, y en el lab no hay NAT Gateway.
# -----------------------------------------------------------------------------
data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "publicas" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
  filter {
    name   = "map-public-ip-on-launch"
    values = ["true"]
  }
}

# -----------------------------------------------------------------------------
# LA RUTA A INTERNET. No es un extra: sin esto no funciona NADA.
#
# La VPC por defecto de este Learner Lab tiene el internet gateway adjunto pero
# su tabla de rutas principal NO trae la ruta 0.0.0.0/0 hacia el. Una VPC por
# defecto normal si la trae; esta no. El sintoma no dice nada de rutas:
#
#   Fargate   -> ResourceInitializationError: unable to pull registry auth:
#                There is a connection issue between the task and Amazon ECR
#   Beanstalk -> "None of the instances are sending data", y a los ~18 minutos
#                LaunchWaitCondition failed
#
# Los dos son lo mismo: la maquina arranca, pero no puede hablar con AWS. Tres
# intentos de Beanstalk se perdieron persiguiendo la plataforma y el tipo de
# instancia cuando el problema estaba aqui.
#
# Se declara como recurso para que quede en codigo y le pase a cualquiera que
# clone esto. Si la cuenta ya tuviera la ruta, el apply falla con
# RouteAlreadyExists y basta con borrar este bloque.
# -----------------------------------------------------------------------------
data "aws_internet_gateway" "default" {
  filter {
    name   = "attachment.vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

data "aws_route_table" "principal" {
  vpc_id = data.aws_vpc.default.id
  filter {
    name   = "association.main"
    values = ["true"]
  }
}

resource "aws_route" "salida_a_internet" {
  route_table_id         = data.aws_route_table.principal.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = data.aws_internet_gateway.default.id
}

# El ID de la cuenta se pregunta en vez de quemarse: asi el archivo sirve en el
# lab de cualquiera. Se declara aqui y no en otro archivo para que fargate.tf se
# sostenga solo.
data "aws_caller_identity" "actual" {}

# -----------------------------------------------------------------------------
# El registro de la imagen.
# -----------------------------------------------------------------------------
resource "aws_ecr_repository" "backend" {
  name = "dsy1107-backend-${var.estudiante}"

  # Que "terraform destroy" no falle porque quedaron imagenes dentro. Mismo
  # motivo que el force_destroy del bucket de bundles.
  force_delete = true

  image_scanning_configuration {
    scan_on_push = false
  }
}

# -----------------------------------------------------------------------------
# Los logs.
#
# Con Beanstalk habia que pedirlos en dos pasos (request + retrieve, lamina 23).
# Aqui el stdout del contenedor va directo a CloudWatch:
#
#   aws logs tail /ecs/dsy1107-backend-<apellido> --follow
# -----------------------------------------------------------------------------
resource "aws_cloudwatch_log_group" "backend" {
  name              = "/ecs/dsy1107-backend-${var.estudiante}"
  retention_in_days = 7
}

# -----------------------------------------------------------------------------
# El grupo de seguridad de la task.
#
# Sin balanceador, quien llama es el API Gateway, y un HTTP API llama por
# internet: no hay VPC Link. Asi que el 8080 queda abierto a 0.0.0.0/0 y
# cualquiera que averigue la IP puede llamar al backend sin token.
#
# Eso NO es un descuido, es la misma exposicion que tenia el entorno de
# Beanstalk, y conviene decirlo en clase: la unica puerta real es el API
# Gateway, y este servicio confia en que nadie lo alcance por el costado. La
# mitigacion de verdad es que el backend valide el token por su cuenta
# (spring-boot-starter-oauth2-resource-server), que es la extension 1 del README
# del backend.
# -----------------------------------------------------------------------------
resource "aws_security_group" "tarea" {
  name        = "dsy1107-backend-${var.estudiante}-tarea"
  description = "Backend en Fargate: 8080 publico, porque quien llama es el API Gateway"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description = "8080 desde internet: el HTTP API del gateway no entra por la VPC"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "Salida para bajar la imagen de ECR y llamar a mindicador.cl"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# -----------------------------------------------------------------------------
# El cluster, la definicion de task y el servicio.
#
#   cluster  -> donde corren las tasks. En Fargate es solo un nombre.
#   task def -> la receta: que imagen, cuanta CPU, que variables, donde loguea.
#   service  -> cuantas copias de esa receta deben estar vivas.
# -----------------------------------------------------------------------------
resource "aws_ecs_cluster" "backend" {
  name = "dsy1107-backend-${var.estudiante}"
}

resource "aws_ecs_task_definition" "backend" {
  family                   = "dsy1107-backend-${var.estudiante}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 512
  memory                   = 1024

  # En el Learner Lab no se pueden crear roles IAM: se usa el que ya existe,
  # igual que LabInstanceProfile en beanstalk.tf. El de ejecucion es el que baja
  # la imagen de ECR y escribe en CloudWatch.
  execution_role_arn = "arn:aws:iam::${data.aws_caller_identity.actual.account_id}:role/LabRole"

  # La imagen se construye en un Mac que puede ser ARM. Fargate corre x86_64
  # salvo que se le diga lo contrario, asi que el build DEBE ir con
  # --platform linux/amd64 (lo hace publicar-fargate.sh). Si no coinciden, la
  # task muere con "exec format error", que no explica nada.
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([
    {
      name      = "backend"
      image     = "${aws_ecr_repository.backend.repository_url}:latest"
      essential = true

      portMappings = [
        {
          containerPort = 8080
          protocol      = "tcp"
        }
      ]

      environment = [
        { name = "BACKEND_MINDICADOR_TTL", value = "10m" }
      ]

      # Sin balanceador no hay target group que vigile la salud, asi que la
      # vigila ECS desde dentro del contenedor. Es lo que mantiene util al
      # actuator: si el proceso se traba, ECS mata la task y levanta otra.
      # curl viene en la imagen base de Corretto (comprobado).
      healthCheck = {
        command     = ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 60
      }

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.backend.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "backend"
        }
      }
    }
  ])
}

resource "aws_ecs_service" "backend" {
  name            = "dsy1107-backend-${var.estudiante}"
  cluster         = aws_ecs_cluster.backend.id
  task_definition = aws_ecs_task_definition.backend.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets         = data.aws_subnets.publicas.ids
    security_groups = [aws_security_group.tarea.id]

    # Obligatorio aqui por partida doble: sin IP publica la task no puede bajar
    # la imagen de ECR (esta VPC no tiene NAT Gateway), y ademas es la direccion
    # por la que la alcanza el API Gateway.
    assign_public_ip = true
  }

  # En false a proposito (es el valor por defecto, escrito para que se vea): si
  # esperara el estado estable, el primer apply se colgaria hasta agotarse
  # porque todavia no hay imagen en ECR.
  wait_for_steady_state = false
}

# -----------------------------------------------------------------------------
# Salidas
#
# No hay output con la URL del backend: sin balanceador, la direccion es la IP
# publica de la task, cambia en cada despliegue y Terraform no la conoce. La
# resuelve publicar-fargate.sh al final de cada publicacion.
# -----------------------------------------------------------------------------

output "ecs_repositorio" {
  description = "Repositorio ECR al que publicar la imagen."
  value       = aws_ecr_repository.backend.repository_url
}

output "ecs_cluster" {
  description = "Nombre del cluster. Lo lee publicar-fargate.sh."
  value       = aws_ecs_cluster.backend.name
}

output "ecs_servicio" {
  description = "Nombre del servicio. Lo lee publicar-fargate.sh."
  value       = aws_ecs_service.backend.name
}

output "ecs_logs" {
  description = "Como ver los logs del contenedor en vivo."
  value       = "aws logs tail ${aws_cloudwatch_log_group.backend.name} --follow"
}
