#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${XJICLOUD_BACKEND_BASE_URL:-http://127.0.0.1:8080}"
BASE_URL="${BASE_URL%/}"
ADMIN_USERNAME="${XJICLOUD_ADMIN_USERNAME:-}"
ADMIN_PASSWORD="${XJICLOUD_ADMIN_PASSWORD:-}"
unset XJICLOUD_ADMIN_PASSWORD
ADMIN_TOKEN=""
API_BODY=""
INPUT_VALUE=""

usage() {
  cat <<'EOF'
Usage:
  manage-cloud-access-keys.sh list <resource>
  manage-cloud-access-keys.sh show <id>
  manage-cloud-access-keys.sh create <name> <resources>
  manage-cloud-access-keys.sh update <id>
  manage-cloud-access-keys.sh test <id> <resource>
  manage-cloud-access-keys.sh test-draft <resource>
  manage-cloud-access-keys.sh select <id> <resource>
  manage-cloud-access-keys.sh delete <id>

Resources: ECI_OPENAPI, OSS. Separate multiple resources with commas.
Set XJICLOUD_BACKEND_BASE_URL, XJICLOUD_ADMIN_USERNAME and
XJICLOUD_ADMIN_PASSWORD, or enter the admin credentials when prompted.
EOF
}

fail() {
  printf '%s\n' "$*" >&2
  exit 1
}

require_tools() {
  command -v curl >/dev/null 2>&1 || fail "curl is required"
  command -v jq >/dev/null 2>&1 || fail "jq is required"
}

validate_resource() {
  case "$1" in
    ECI_OPENAPI|OSS) ;;
    *) fail "resource must be ECI_OPENAPI or OSS" ;;
  esac
}

parse_resources() {
  local raw="$1"
  local item
  local -a parsed resources
  IFS=',' read -r -a parsed <<< "$raw"
  resources=()
  for item in "${parsed[@]}"; do
    item="${item#"${item%%[![:space:]]*}"}"
    item="${item%"${item##*[![:space:]]}"}"
    validate_resource "$item"
    if [[ ! " ${resources[*]} " == *" $item "* ]]; then
      resources+=("$item")
    fi
  done
  [[ ${#resources[@]} -gt 0 ]] || fail "at least one resource is required"
  printf '%s\n' "${resources[@]}" | jq -R . | jq -s .
}

read_value() {
  local prompt="$1"
  IFS= read -r -p "$prompt" INPUT_VALUE || fail "input cancelled"
}

read_secret() {
  local prompt="$1"
  IFS= read -r -s -p "$prompt" INPUT_VALUE || fail "input cancelled"
  printf '\n' >&2
}

request() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local raw status response message
  local -a args

  args=(
    --silent
    --show-error
    --connect-timeout 10
    --max-time 60
    --write-out $'\n%{http_code}'
    --request "$method"
    --header "Accept: application/json"
  )
  if [[ -n "$ADMIN_TOKEN" ]]; then
    args+=(--header "Authorization: Bearer $ADMIN_TOKEN")
  fi
  if [[ -n "$body" ]]; then
    args+=(--header "Content-Type: application/json" --data-binary @-)
    if ! raw="$(curl "${args[@]}" "$BASE_URL$path" <<< "$body")"; then
      fail "request failed: $method $path"
    fi
  else
    if ! raw="$(curl "${args[@]}" "$BASE_URL$path")"; then
      fail "request failed: $method $path"
    fi
  fi

  status="${raw##*$'\n'}"
  response="${raw%$'\n'*}"
  if [[ ! "$status" =~ ^2[0-9][0-9]$ ]]; then
    message="$(jq -r '.message // empty' <<< "$response" 2>/dev/null || true)"
    [[ -n "$message" ]] || message="HTTP $status"
    fail "$message"
  fi
  if ! jq -e '.success == true' <<< "$response" >/dev/null 2>&1; then
    message="$(jq -r '.message // "invalid API response"' <<< "$response" 2>/dev/null || true)"
    fail "$message"
  fi
  API_BODY="$response"
}

login() {
  local login_body

  if [[ -z "$ADMIN_USERNAME" ]]; then
    read_value "Admin username: "
    ADMIN_USERNAME="$INPUT_VALUE"
  fi
  if [[ -z "$ADMIN_PASSWORD" ]]; then
    read_secret "Admin password: "
    ADMIN_PASSWORD="$INPUT_VALUE"
  fi

  login_body="$(printf '%s\0%s\0' "$ADMIN_USERNAME" "$ADMIN_PASSWORD" | jq -Rs 'split("\u0000") as $values | {username: $values[0], password: $values[1]}')"
  request POST /api/v1/admin/auth/login "$login_body"
  ADMIN_TOKEN="$(jq -er '.data.token | select(type == "string" and length > 0)' <<< "$API_BODY")" \
    || fail "admin login response did not include a token"
  API_BODY=""
  unset ADMIN_PASSWORD login_body
}

print_data() {
  jq '.data' <<< "$API_BODY"
}

prompt_access_key_pair() {
  read_value "AccessKeyId: "
  ACCESS_KEY_ID="$INPUT_VALUE"
  [[ -n "$ACCESS_KEY_ID" ]] || fail "AccessKeyId cannot be empty"
  read_secret "AccessKeySecret: "
  ACCESS_KEY_SECRET="$INPUT_VALUE"
  [[ -n "$ACCESS_KEY_SECRET" ]] || fail "AccessKeySecret cannot be empty"
}

main() {
  local command="${1:-}"
  local id=""
  local resource=""
  local resources_json=""
  local body=""
  local name=""
  local access_key_id=""
  local access_key_secret=""
  local typed_resources=""

  if [[ "$command" == "--help" || "$command" == "-h" ]]; then
    usage
    return 0
  fi
  [[ -n "$command" ]] || { usage >&2; return 2; }
  require_tools

  case "$command" in
    list)
      [[ $# -eq 2 ]] || fail "usage: list <resource>"
      validate_resource "$2"
      resource="$2"
      ;;
    show)
      [[ $# -eq 2 ]] || fail "usage: show <id>"
      id="$2"
      ;;
    create)
      [[ $# -eq 3 ]] || fail "usage: create <name> <resources>"
      name="$2"
      resources_json="$(parse_resources "$3")"
      ;;
    update)
      [[ $# -eq 2 ]] || fail "usage: update <id>"
      id="$2"
      ;;
    test)
      [[ $# -eq 3 ]] || fail "usage: test <id> <resource>"
      id="$2"
      validate_resource "$3"
      resource="$3"
      ;;
    test-draft)
      [[ $# -eq 2 ]] || fail "usage: test-draft <resource>"
      validate_resource "$2"
      resource="$2"
      ;;
    select)
      [[ $# -eq 3 ]] || fail "usage: select <id> <resource>"
      id="$2"
      validate_resource "$3"
      resource="$3"
      ;;
    delete)
      [[ $# -eq 2 ]] || fail "usage: delete <id>"
      id="$2"
      ;;
    *)
      usage >&2
      fail "unknown command: $command"
      ;;
  esac

  login
  case "$command" in
    list)
      request GET "/api/v1/admin/cloud-access-keys?resource=$resource"
      ;;
    show)
      request GET "/api/v1/admin/cloud-access-keys/$id"
      ;;
    create)
      prompt_access_key_pair
      body="$(printf '%s\0%s\0%s\0%s\0' "$name" "$ACCESS_KEY_ID" "$ACCESS_KEY_SECRET" "$resources_json" \
        | jq -Rs 'split("\u0000") as $values | {name: $values[0], accessKeyId: $values[1], accessKeySecret: $values[2], resources: ($values[3] | fromjson)}')"
      request POST /api/v1/admin/cloud-access-keys "$body"
      unset ACCESS_KEY_SECRET body
      ;;
    update)
      read_value "Name (blank keeps current): "
      name="$INPUT_VALUE"
      read_value "AccessKeyId (blank keeps current): "
      access_key_id="$INPUT_VALUE"
      read_secret "AccessKeySecret (blank keeps current): "
      access_key_secret="$INPUT_VALUE"
      if [[ -n "$access_key_id" || -n "$access_key_secret" ]]; then
        [[ -n "$access_key_id" && -n "$access_key_secret" ]] \
          || fail "provide both AccessKeyId and AccessKeySecret to update credentials"
      fi
      read_value "Resources (comma-separated, blank keeps current): "
      typed_resources="$INPUT_VALUE"
      if [[ -n "$typed_resources" ]]; then
        resources_json="$(parse_resources "$typed_resources")"
      fi
      body="$(printf '%s\0%s\0%s\0' "$name" "$access_key_id" "$access_key_secret" \
        | jq -Rs 'split("\u0000") as $values | {name: (if $values[0] == "" then null else $values[0] end), accessKeyId: (if $values[1] == "" then null else $values[1] end), accessKeySecret: (if $values[2] == "" then null else $values[2] end)}')"
      if [[ -n "$typed_resources" ]]; then
        body="$(jq --argjson resources "$resources_json" '. + {resources: $resources}' <<< "$body")"
      fi
      request PUT "/api/v1/admin/cloud-access-keys/$id" "$body"
      unset ACCESS_KEY_SECRET access_key_secret body
      ;;
    test)
      body="$(jq -cn --arg resource "$resource" '{resource: $resource}')"
      request POST "/api/v1/admin/cloud-access-keys/$id/test" "$body"
      ;;
    test-draft)
      prompt_access_key_pair
      body="$(printf '%s\0%s\0%s\0' "$resource" "$ACCESS_KEY_ID" "$ACCESS_KEY_SECRET" \
        | jq -Rs 'split("\u0000") as $values | {resource: $values[0], accessKeyId: $values[1], accessKeySecret: $values[2]}')"
      request POST /api/v1/admin/cloud-access-keys/test "$body"
      unset ACCESS_KEY_SECRET body
      ;;
    select)
      body="$(jq -cn --arg resource "$resource" '{resource: $resource}')"
      request POST "/api/v1/admin/cloud-access-keys/$id/select" "$body"
      ;;
    delete)
      read_value "Type DELETE to confirm removal of $id: "
      [[ "$INPUT_VALUE" == "DELETE" ]] || fail "delete cancelled"
      request DELETE "/api/v1/admin/cloud-access-keys/$id"
      ;;
  esac
  print_data
}

main "$@"
