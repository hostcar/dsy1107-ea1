#!/usr/bin/env bash
#
# Publica una version nueva del backend en ECS Fargate.
#
# Mismo reparto que con Amplify y que el que la guia 1.3.8 planteaba para
# Beanstalk: Terraform crea lo que existe una vez (cluster, balanceador,
# servicio) y este script publica lo que cambia en cada commit (la imagen).
#
#   ./scripts/publicar-fargate.sh
#
# Lee cluster, servicio y repositorio de los outputs de Terraform, asi que no
# hay nombres repetidos en dos sitios.

set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"
RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TF="terraform -chdir=${RAIZ}/terraform"

# ---------------------------------------------------------------------------
# De donde salen los nombres.
#
# En local, de los outputs de Terraform: no hay que repetirlos en ningun sitio.
# En el pipeline no sirve, porque el estado de Terraform es local y esta en
# .gitignore: alli los valores llegan como variables de entorno (variables del
# repositorio en GitHub Actions).
#
# La variable de entorno manda; Terraform es el respaldo. Asi el MISMO script
# corre en los dos lados, que era el punto: una sola definicion del
# procedimiento.
# ---------------------------------------------------------------------------
leer() {
  local desde_entorno="${!1:-}"
  if [ -n "$desde_entorno" ]; then
    echo "$desde_entorno"
  elif command -v terraform >/dev/null 2>&1 && $TF output -raw "$2" >/dev/null 2>&1; then
    $TF output -raw "$2"
  else
    echo "Falta \$$1 y no hay salida '$2' en Terraform." >&2
    return 1
  fi
}

echo "==> Leyendo la configuracion"
REPO="$(leer ECR_REPO ecs_repositorio)"
CLUSTER="$(leer ECS_CLUSTER ecs_cluster)"
SERVICIO="$(leer ECS_SERVICE ecs_servicio)"
API_ID="$(leer API_ID api_id)"
INTEGRACION_ID="$(leer INTEGRATION_ID integracion_id)"

# Etiqueta unica por despliegue, como pedia la lamina 19: reutilizar una
# etiqueta hace imposible saber que esta corriendo, y volver atras.
VERSION="v$(date +%Y%m%d-%H%M%S)"

# ./mvnw y no "mvn": el wrapper baja la version de Maven que declara
# .mvn/wrapper/maven-wrapper.properties, asi el proyecto compila igual en una
# maquina sin Maven instalado. Es el mismo motivo por el que el front usa npm
# desde package.json y no una instalacion global.
echo "==> 1/5 Construyendo el jar"
( cd "${RAIZ}/backend" && ./mvnw --batch-mode --no-transfer-progress clean verify )

# --platform linux/amd64 NO es opcional: si construyes en un Mac con Apple
# Silicon, la imagen sale arm64 y la task de Fargate muere con "exec format
# error", que no dice nada util. Debe coincidir con el runtime_platform de
# ecs.tf.
echo "==> 2/5 Construyendo la imagen (${VERSION}, linux/amd64)"
docker build --platform linux/amd64 \
  -t "${REPO}:${VERSION}" -t "${REPO}:latest" "${RAIZ}/backend"

echo "==> 3/5 Autenticando contra ECR"
aws ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "${REPO%%/*}"

echo "==> 4/5 Subiendo la imagen"
docker push "${REPO}:${VERSION}"
docker push "${REPO}:latest"

# La task definition apunta a :latest, asi que no hay que registrarla de nuevo:
# basta con obligar al servicio a levantar tasks nuevas, que vuelven a bajar la
# etiqueta. La imagen con la etiqueta ${VERSION} queda ademas como historial
# para poder volver atras.
echo "==> 5/5 Redesplegando el servicio"
aws ecs update-service --region "$REGION" \
  --cluster "$CLUSTER" --service "$SERVICIO" \
  --force-new-deployment >/dev/null

echo "    Esperando a que el despliegue termine..."
INTENTOS=60
for INTENTO in $(seq 1 $INTENTOS); do
  # Sin "|| true" el set -e mata el script con el error crudo de AWS. Interesa
  # distinguir dos cosas que se parecen y no son lo mismo: un despliegue que va
  # mal, y un lab que se cerro debajo.
  if ! LEIDO="$(aws ecs describe-services --region "$REGION" \
    --cluster "$CLUSTER" --services "$SERVICIO" \
    --query "services[0].[deployments[0].rolloutState,runningCount,desiredCount]" \
    --output text 2>&1)"; then
    if echo "$LEIDO" | grep -q "explicit deny"; then
      echo >&2
      echo "Se cerro la sesion del Learner Lab en mitad del despliegue." >&2
      echo "La imagen YA se subio y el redespliegue YA se lanzo; solo se perdio" >&2
      echo "el sondeo. Reabre el lab, actualiza las credenciales y vuelve a" >&2
      echo "correr esto para confirmar el estado." >&2
      exit 1
    fi
    echo "No se pudo consultar el servicio: $LEIDO" >&2
    exit 1
  fi
  ESTADO="$(echo "$LEIDO" | awk '{print $1}')"
  CORRIENDO="$(echo "$LEIDO" | awk '{print $2}')"
  DESEADAS="$(echo "$LEIDO" | awk '{print $3}')"
  echo "    ${ESTADO}  ${CORRIENDO}/${DESEADAS}"

  # Los tres campos, no solo el estado: mismo criterio que en Amplify, un
  # despliegue a medias no pasa por bueno.
  if [ "$ESTADO" = "COMPLETED" ] && [ "$CORRIENDO" = "$DESEADAS" ]; then
    break
  fi
  if [ "$ESTADO" = "FAILED" ]; then
    echo
    echo "FALLO el despliegue. Los logs del contenedor:" >&2
    echo "    aws logs tail /ecs/${SERVICIO} --follow" >&2
    exit 1
  fi
  if [ "$INTENTO" -eq "$INTENTOS" ]; then
    echo "Se agoto la espera. Revisa:  aws logs tail /ecs/${SERVICIO} --follow" >&2
    exit 1
  fi
  sleep 10
done

# ---------------------------------------------------------------------------
# Reapuntar el API Gateway.
#
# Esto es lo que un balanceador haria innecesario: sin ALB, cada despliegue
# levanta una task con IP publica nueva, asi que la integracion del gateway
# queda apuntando a una direccion muerta hasta que alguien la actualice.
#
# La IP no se pregunta directamente: la task expone una interfaz de red (ENI),
# y la IP publica cuelga de esa interfaz. Son tres saltos.
# ---------------------------------------------------------------------------
echo "==> Reapuntando el API Gateway a la task nueva"

TAREA="$(aws ecs list-tasks --region "$REGION" \
  --cluster "$CLUSTER" --service-name "$SERVICIO" --desired-status RUNNING \
  --query "taskArns[0]" --output text)"

ENI="$(aws ecs describe-tasks --region "$REGION" \
  --cluster "$CLUSTER" --tasks "$TAREA" \
  --query "tasks[0].attachments[0].details[?name=='networkInterfaceId'].value" \
  --output text)"

IP="$(aws ec2 describe-network-interfaces --region "$REGION" \
  --network-interface-ids "$ENI" \
  --query "NetworkInterfaces[0].Association.PublicIp" --output text)"

if [ -z "$IP" ] || [ "$IP" = "None" ]; then
  echo "No se pudo resolver la IP publica de la task ($TAREA)." >&2
  exit 1
fi

aws apigatewayv2 update-integration --region "$REGION" \
  --api-id "$API_ID" \
  --integration-id "$INTEGRACION_ID" \
  --integration-uri "http://${IP}:8080/datos" >/dev/null

echo
echo "OK  ${VERSION} desplegada."
echo "    backend directo : http://${IP}:8080/actuator/health"
if URL_API="$($TF output -raw url_datos_protegido 2>/dev/null)"; then
  echo "    via API Gateway : ${URL_API}   (401 sin token)"
fi
echo
echo "    La IP cambia en cada despliegue; por eso este script reapunta el"
echo "    gateway. apigateway.tf lo sabe: ignore_changes en integration_uri."
