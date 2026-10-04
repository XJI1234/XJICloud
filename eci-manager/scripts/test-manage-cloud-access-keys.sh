#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT="$SCRIPT_DIR/manage-cloud-access-keys.sh"
TEST_TMP="$(mktemp -d)"
export MOCK_CURL_CALLS="$TEST_TMP/curl-calls"
export MOCK_CREATE_BODY="$TEST_TMP/create-body.json"
export XJICLOUD_BACKEND_BASE_URL="http://backend.test"
export XJICLOUD_ADMIN_USERNAME="test-admin"
export XJICLOUD_ADMIN_PASSWORD="test-password"

curl() {
  local args="$*"
  printf '%s\n' "$args" >> "$MOCK_CURL_CALLS"

  if [[ "$args" == *"/api/v1/admin/auth/login"* ]]; then
    printf '%s\n200' '{"success":true,"message":null,"data":{"token":"test-admin-token","username":"test-admin"}}'
    return
  fi

  if [[ "$args" == *"/api/v1/admin/cloud-access-keys?resource=OSS"* ]]; then
    if [[ "$args" != *"Authorization: Bearer test-admin-token"* ]]; then
      printf 'missing admin bearer token\n' >&2
      return 1
    fi
    printf '%s\n200' '{"success":true,"message":null,"data":{"resource":"OSS","currentId":null,"items":[]}}'
    return
  fi

  if [[ "$args" == *"--request POST"* && "$args" == *"/api/v1/admin/cloud-access-keys" ]]; then
    cat > "$MOCK_CREATE_BODY"
    printf '%s\n200' '{"success":true,"message":null,"data":{"id":"test-id","name":"test-key","accessKeyHint":"LT****01","secretConfigured":true,"resources":["ECI_OPENAPI"],"selectedResources":[]}}'
    return
  fi

  printf 'unexpected request: %s\n' "$args" >&2
  return 1
}
export -f curl

help_output="$(bash "$SCRIPT" --help)"
[[ "$help_output" == *"list <resource>"* ]]

: > "$MOCK_CURL_CALLS"
list_output="$(bash "$SCRIPT" list OSS)"
jq -e '.resource == "OSS" and (.items | length == 0)' <<< "$list_output" >/dev/null
grep -Fq 'Authorization: Bearer test-admin-token' "$MOCK_CURL_CALLS"
grep -Fq 'http://backend.test/api/v1/admin/cloud-access-keys?resource=OSS' "$MOCK_CURL_CALLS"

create_output="$(printf 'LTAIfakekey0001\nfake-secret-value\n' | bash "$SCRIPT" create test-key ECI_OPENAPI)"
jq -e '.name == "test-key" and .accessKeyId == "LTAIfakekey0001" and .accessKeySecret == "fake-secret-value" and .resources == ["ECI_OPENAPI"]' \
  "$MOCK_CREATE_BODY" >/dev/null
[[ "$create_output" != *"fake-secret-value"* ]]
if grep -Fq 'fake-secret-value' "$MOCK_CURL_CALLS"; then
  printf 'AccessKeySecret appeared in curl arguments\n' >&2
  exit 1
fi

: > "$MOCK_CURL_CALLS"
if bash "$SCRIPT" list INVALID > "$TEST_TMP/invalid-resource-output" 2>&1; then
  printf 'invalid resource was accepted\n' >&2
  exit 1
fi
[[ ! -s "$MOCK_CURL_CALLS" ]]
grep -Fq 'resource must be ECI_OPENAPI or OSS' "$TEST_TMP/invalid-resource-output"

printf 'manage-cloud-access-keys checks passed\n'
