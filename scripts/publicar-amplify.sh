#!/usr/bin/env bash
# =============================================================================
#
#   Uso: publicar-amplify.sh <app-id> <rama> <directorio-compilado>
#
# Devuelve 0 solo si Amplify reporta SUCCEED.
# =============================================================================

set -euo pipefail

APP_ID="${1:?Falta el app-id de Amplify}"
RAMA="${2:?Falta la rama}"
DIRECTORIO="${3:?Falta el directorio compilado}"

INTENTOS="${INTENTOS:-60}"
ESPERA="${ESPERA:-5}"

for h in aws jq zip curl; do
  command -v "$h" >/dev/null 2>&1 || { echo "Falta '$h' en el PATH." >&2; exit 1; }
done

# Se valida lo que se va a publicar, no lo que se cree que se compilo.
[[ -f "$DIRECTORIO/index.html" ]] \
  || { echo "No existe $DIRECTORIO/index.html" >&2; exit 1; }
[[ -f "$DIRECTORIO/config.json" ]] \
  || { echo "config.json no llego al bundle (revisa 'assets' en angular.json)" >&2; exit 1; }

ZIP="$(mktemp -t amplify-XXXXXX).zip"
limpiar() { rm -f "$ZIP"; }
trap limpiar EXIT

# Se comprime el CONTENIDO del directorio: index.html tiene que quedar en la
# raiz del zip o Amplify sirve un 404. El .example no se publica.
( cd "$DIRECTORIO" && zip -qr "$ZIP" . -x 'config.example.json' )
echo "Empaquetado: $(du -h "$ZIP" | cut -f1)"

despliegue="$(aws amplify create-deployment \
  --app-id "$APP_ID" --branch-name "$RAMA" --output json)"
job="$(jq -r '.jobId' <<< "$despliegue")"
subida="$(jq -r '.zipUploadUrl' <<< "$despliegue")"

curl -sS --fail-with-body -X PUT -T "$ZIP" "$subida" > /dev/null
aws amplify start-deployment \
  --app-id "$APP_ID" --branch-name "$RAMA" --job-id "$job" > /dev/null
echo "Job $job enviado"

estado=PENDING
for _ in $(seq 1 "$INTENTOS"); do
  estado="$(aws amplify get-job --app-id "$APP_ID" --branch-name "$RAMA" \
            --job-id "$job" --query 'job.summary.status' --output text)"
  case "$estado" in
    SUCCEED)          break ;;
    FAILED|CANCELLED) echo "El despliegue termino en $estado (job $job)" >&2; exit 1 ;;
  esac
  sleep "$ESPERA"
done

if [[ "$estado" != SUCCEED ]]; then
  echo "El job $job sigue en $estado tras $((INTENTOS * ESPERA)) segundos" >&2
  exit 1
fi

echo "Publicado (job $job)"
