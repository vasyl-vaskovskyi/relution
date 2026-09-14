#!/usr/bin/env bash
# Generates a small, mixed request load so every panel of the "App Store service" Grafana dashboard has data
# (docs/operations/observability.md#demo-traffic). Calls the real Apple APIs, but stays well inside the Search budget:
# a round makes at most 3 Search and 6 Lookup calls to Apple; rounds after the third are served from the caches.
#
#   scripts/demo-traffic.sh                 # 2 rounds, 70 s apart (one OTLP export in between)
#   ROUNDS=3 PAUSE=70 scripts/demo-traffic.sh
#   BASE_URL=http://localhost:18080 scripts/demo-traffic.sh
#
# Reads APPSTORE_AUTH_CLIENT_ID and APPSTORE_AUTH_CLIENT_SECRET from the environment or from .env in the repository
# root. Never prints the token or the secret; both reach curl through stdin, not the command line.
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Explicitly set values win over .env
base_url_override="${BASE_URL:-}"
if [[ -f "$root_dir/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$root_dir/.env"
  set +a
fi
BASE_URL="${base_url_override:-${BASE_URL:-http://localhost:8080}}"
ROUNDS="${ROUNDS:-2}"
PAUSE="${PAUSE:-70}"
client_id="${APPSTORE_AUTH_CLIENT_ID:-}"
client_secret="${APPSTORE_AUTH_CLIENT_SECRET:-}"

for tool in curl jq; do
  command -v "$tool" >/dev/null || { echo "demo-traffic.sh needs $tool" >&2; exit 2; }
done
[[ "$ROUNDS" =~ ^[1-9][0-9]*$ ]] || { echo "ROUNDS must be a positive integer" >&2; exit 2; }
[[ "$PAUSE" =~ ^[0-9]+$ ]] || { echo "PAUSE must be a whole number of seconds" >&2; exit 2; }
if [[ -z "$client_id" || -z "$client_secret" ]]; then
  echo "APPSTORE_AUTH_CLIENT_ID and APPSTORE_AUTH_CLIENT_SECRET must be set (environment or .env)" >&2
  exit 2
fi

body="$(mktemp)"
trap 'rm -f "$body"' EXIT

token=""
requests=0
statuses=""

# curl config strings are double-quoted; escape backslashes and quotes
config_escape() {
  local value="${1//\\/\\\\}"
  printf '%s' "${value//\"/\\\"}"
}

# call METHOD URL [none|basic|bearer]: prints the HTTP status, stores the body in $body
call() {
  local method="$1" url="$2" auth="${3:-none}" status
  local -a args=(--silent --show-error --max-time 30 --output "$body" --write-out '%{http_code}' --request "$method")
  : >"$body"
  case "$auth" in
    basic)
      status="$(printf 'user = "%s:%s"\n' "$(config_escape "$client_id")" "$(config_escape "$client_secret")" |
        curl "${args[@]}" --config - "$url")" || status="000"
      ;;
    bearer)
      status="$(printf 'Authorization: Bearer %s\n' "$token" | curl "${args[@]}" --header @- "$url")" || status="000"
      ;;
    *)
      status="$(curl "${args[@]}" "$url")" || status="000"
      ;;
  esac
  printf '%s' "$status"
}

# hit PATH_AND_QUERY [none|bearer]: one API request, counted by status
hit() {
  local status
  status="$(call GET "$BASE_URL$1" "${2:-bearer}")"
  requests=$((requests + 1))
  statuses+="$status"$'\n'
}

status="$(call POST "$BASE_URL/auth/token" basic)"
if [[ "$status" != 200 ]]; then
  echo "POST /auth/token returned HTTP $status; is the stack running at $BASE_URL?" >&2
  exit 1
fi
token="$(jq -r '.accessToken // empty' "$body")"
[[ -n "$token" ]] || { echo "POST /auth/token returned no access token" >&2; exit 1; }

# Each round uses its own storefront and search terms, so it reaches Apple even though earlier rounds filled the caches.
# Rounds beyond this list repeat the last entry and are answered from the caches.
storefronts=("de de" "us en" "gb en")
terms=("pages numbers zqxjvkwpqz" "keynote final%20cut qzvxkjwpzq" "notes freeform wpqzqxjvkz")

for ((round = 1; round <= ROUNDS; round++)); do
  index=$((round <= ${#storefronts[@]} ? round - 1 : ${#storefronts[@]} - 1))
  read -r cc lang <<<"${storefronts[$index]}"
  read -r term_a term_b term_empty <<<"${terms[$index]}"
  # Searches: two terms (the repeats are cache hits) and one without results (outcome empty)
  hit "/api/v1/apps?term=$term_a&cc=$cc&limit=5"
  hit "/api/v1/apps?term=$term_a&cc=$cc&limit=5"
  hit "/api/v1/apps?term=$term_a&cc=$cc&limit=5"
  hit "/api/v1/apps?term=$term_b&cc=$cc&limit=5"
  hit "/api/v1/apps?term=$term_b&cc=$cc&limit=5"
  hit "/api/v1/apps?term=$term_empty&cc=$cc&limit=5"
  # Details on iOS and Mac: Pages (both), Final Cut Pro (Mac only), 1234094465 (iOS only), an unknown id
  hit "/api/v1/apps/361309726?cc=$cc&l=$lang"
  hit "/api/v1/apps/361309726?cc=$cc&l=$lang"
  hit "/api/v1/apps/361309726?cc=$cc&l=$lang&platform=mac"
  hit "/api/v1/apps/424389933?cc=$cc&l=$lang&platform=mac"
  hit "/api/v1/apps/424389933?cc=$cc&l=$lang&platform=mac"
  hit "/api/v1/apps/1234094465?cc=$cc&l=$lang"
  hit "/api/v1/apps/1234094465?cc=$cc&l=$lang&platform=mac"
  hit "/api/v1/apps/1?cc=$cc&l=$lang"
  # Rejected locally without an Apple call: unsupported storefront, missing cc, no token
  hit "/api/v1/apps?term=pages&cc=cu"
  hit "/api/v1/apps/361309726?cc=cu&l=es"
  hit "/api/v1/apps?term=pages"
  hit "/api/v1/apps?term=pages&cc=de" none
  if ((round < ROUNDS)); then
    # Wait for an OTLP export: a series first appears with its running total, so only later changes show as rates
    sleep "$PAUSE"
  fi
done

summary="$(printf '%s' "$statuses" | sort | uniq -c | awk '{ printf "%s HTTP %s: %s", (NR > 1 ? "," : ""), $2, $1 }')"
echo "sent $requests requests in $ROUNDS round(s) to $BASE_URL;$summary"
echo "metrics reach Grafana after the next OTLP export (up to a minute)"
