#!/usr/bin/env bash
# =============================================================================
# Escribe frontend/public/config.json, que es lo que ConfigService lee en
# runtime antes del primer render.
#
# Lo usan el despliegue local (deploy.sh) y GitHub Actions (frontend.yml). Vive
# aqui, y no duplicado en cada uno, porque las dos rutas tienen que producir
# exactamente el mismo archivo con exactamente las mismas validaciones.
#
# Entrada, por variables de entorno:
#   REGION  COGNITO_DOMAIN  CLIENT_ID  REDIRECT_URI  API_URL
#
# Opcional:
#   DESTINO   ruta del archivo a escribir
#             (por defecto: <raiz>/frontend/public/config.json)
#   EXIGIR_HTTPS=1  falla si REDIRECT_URI no es https, para no publicar en
#                   Amplify un bundle que apunta a localhost
# =============================================================================

set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DESTINO="${DESTINO:-$RAIZ/frontend/public/config.json}"

command -v jq >/dev/null 2>&1 || { echo "Falta 'jq' en el PATH." >&2; exit 1; }

faltan=()
for v in REGION COGNITO_DOMAIN CLIENT_ID REDIRECT_URI API_URL; do
  [[ -n "${!v:-}" ]] || faltan+=("$v")
done
if [[ ${#faltan[@]} -gt 0 ]]; then
  echo "Faltan valores: ${faltan[*]}" >&2
  exit 1
fi

# Con el redirectUri de localhost el build compila igual y la app solo falla en
# el navegador, con un redirect_mismatch que no menciona este paso.
if [[ "${EXIGIR_HTTPS:-0}" == "1" && "$REDIRECT_URI" != https://* ]]; then
  echo "REDIRECT_URI vale '$REDIRECT_URI'; debe ser la URL de Amplify, no localhost." >&2
  exit 1
fi

mkdir -p "$(dirname "$DESTINO")"
jq -n \
  --arg region        "$REGION" \
  --arg cognitoDomain "$COGNITO_DOMAIN" \
  --arg clientId      "$CLIENT_ID" \
  --arg redirectUri   "$REDIRECT_URI" \
  --arg apiUrl        "$API_URL" \
  '{region:$region, cognitoDomain:$cognitoDomain, clientId:$clientId, redirectUri:$redirectUri, apiUrl:$apiUrl}' \
  > "$DESTINO"

echo "config.json escrito en $DESTINO (redirectUri: $REDIRECT_URI)"
