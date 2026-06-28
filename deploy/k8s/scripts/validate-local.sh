#!/usr/bin/env bash
# 本地 K8s 部署验收脚本
set -euo pipefail

export KUBECONFIG="${KUBECONFIG:-/etc/rancher/k3s/k3s.yaml}"
FRONTEND_URL="${FRONTEND_URL:-http://192.168.203.133}"
MINIO_URL="${MINIO_URL:-http://192.168.203.133:30900}"

pass=0
fail=0

check() {
  local name="$1"
  shift
  if "$@"; then
    echo "[PASS] ${name}"
    pass=$((pass + 1))
  else
    echo "[FAIL] ${name}"
    fail=$((fail + 1))
  fi
}

echo "=== Pod status ==="
kubectl -n xjicloud get pods

echo ""
echo "=== Health checks ==="
check "MinIO live" curl -sf --connect-timeout 5 "${MINIO_URL}/minio/health/live" -o /dev/null
check "Frontend reachable" curl -sf --connect-timeout 5 "${FRONTEND_URL}/" -o /dev/null
check "Backend health via ingress" curl -sf --connect-timeout 10 "${FRONTEND_URL}/actuator/health" -o /dev/null

echo ""
echo "=== MinIO CORS preflight ==="
if curl -sf -i -X OPTIONS "${MINIO_URL}/xjicloud/" \
  -H "Origin: ${FRONTEND_URL}" \
  -H "Access-Control-Request-Method: PUT" | grep -qi "access-control-allow-origin"; then
  echo "[PASS] MinIO CORS preflight"
  pass=$((pass + 1))
else
  echo "[FAIL] MinIO CORS preflight"
  fail=$((fail + 1))
fi

echo ""
echo "=== Summary: ${pass} passed, ${fail} failed ==="
[[ "${fail}" -eq 0 ]]
