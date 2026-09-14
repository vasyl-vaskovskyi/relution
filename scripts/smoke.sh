#!/usr/bin/env bash
# End-to-end checks against a running instance (docs/challenge/plan.md, "scripts/smoke.sh checks").
# Calls the real Apple APIs, so run it rarely.
#
#   scripts/smoke.sh
#   BASE_URL=http://localhost:18180 MANAGEMENT_URL=http://localhost:18181 scripts/smoke.sh
#
# Reads APPSTORE_AUTH_CLIENT_ID and APPSTORE_AUTH_CLIENT_SECRET from the environment or from .env in the repository root.
# Never prints the token or the secret; both reach curl through stdin, not the command line.
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Explicitly set URLs win over .env
base_url_override="${BASE_URL:-}"
management_url_override="${MANAGEMENT_URL:-}"
if [[ -f "$root_dir/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$root_dir/.env"
  set +a
fi
BASE_URL="${base_url_override:-${BASE_URL:-http://localhost:8080}}"
MANAGEMENT_URL="${management_url_override:-${MANAGEMENT_URL:-http://localhost:8081}}"
client_id="${APPSTORE_AUTH_CLIENT_ID:-}"
client_secret="${APPSTORE_AUTH_CLIENT_SECRET:-}"

for tool in curl jq; do
  command -v "$tool" >/dev/null || { echo "smoke.sh needs $tool" >&2; exit 2; }
done

body="$(mktemp)"
trap 'rm -f "$body"' EXIT

status=""
token=""
number=0
failures=0

# curl config strings are double-quoted; escape backslashes and quotes
config_escape() {
  local value="${1//\\/\\\\}"
  printf '%s' "${value//\"/\\\"}"
}

# call METHOD URL [none|basic|bearer]: stores the status in $status and the body in $body
call() {
  local method="$1" url="$2" auth="${3:-none}"
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
}

# expect DESCRIPTION STATUS [JQ_FILTER]: checks the last response
expect() {
  local description="$1" expected="$2" filter="${3:-}"
  number=$((number + 1))
  if [[ "$status" == "$expected" ]] && { [[ -z "$filter" ]] || jq -e "$filter" "$body" >/dev/null 2>&1; }; then
    printf 'PASS %2d  %s\n' "$number" "$description"
  else
    printf 'FAIL %2d  %s (HTTP %s)\n' "$number" "$description" "$status"
    failures=$((failures + 1))
  fi
}

problem_type() {
  printf '.type == "urn:appstore:problem:%s"' "$1"
}

search="$BASE_URL/api/v1/apps"
details="$BASE_URL/api/v1/apps/361309726"

call GET "$BASE_URL/readyz"
expect "GET /readyz" 200

if [[ -z "$client_id" || -z "$client_secret" ]]; then
  echo "note: APPSTORE_AUTH_CLIENT_ID or APPSTORE_AUTH_CLIENT_SECRET is not set" >&2
fi
call POST "$BASE_URL/auth/token" basic
expect "POST /auth/token with Basic credentials" 200 '(.accessToken | type == "string") and (.accessToken | length > 0)'
if [[ "$status" == 200 ]]; then
  token="$(jq -r '.accessToken // empty' "$body" 2>/dev/null || true)"
fi

call GET "$search?term=pages&cc=de"
expect "search without a token" 401

call GET "$search?term=pages&cc=de&limit=5" bearer
expect "search term=pages cc=de limit=5" 200 '
  .count >= 1
  and all(.items[]; .id | type == "string")
  and all(.items[].price.amount | select(. != null); type == "string")
  and any(.items[].price.amount; type == "string")'

call GET "$search?term=pages" bearer
expect "search without cc" 400 'any(.errors[]?; .field == "cc")'

call GET "$search?term=pages&cc=cu" bearer
expect "search with cc=cu" 400 "$(problem_type unsupported-storefront)"

call GET "$details?cc=de&l=de" bearer
expect "details 361309726 cc=de l=de" 200 '
  .kind == "IOS_APP" and .storefront.cc == "de" and (.version | type == "string") and (.version | length > 0)'

call GET "$details?cc=de&l=de&platform=mac" bearer
expect "details with platform=mac" 200 '.storefront.platform == "mac"'

call GET "$details?cc=de&l=fr" bearer
expect "details with l=fr" 200 '.storefront.language == "de-de"'

call GET "$BASE_URL/api/v1/apps/1?cc=de&l=de" bearer
expect "details for unknown id 1" 404 "$(problem_type app-not-found)"

call GET "$details?l=de" bearer
expect "details without cc" 400

call GET "$BASE_URL/actuator/env"
expect "GET /actuator/env on the API port without a token" 401 '
  .status == 401 and (.title | type == "string")
  and (has("propertySources") | not) and (has("activeProfiles") | not)'

call GET "$MANAGEMENT_URL/actuator/env"
expect "GET /actuator/env on the management port" 404

echo
if ((failures > 0)); then
  echo "$failures of $number checks failed"
  exit 1
fi
echo "all $number checks passed"
