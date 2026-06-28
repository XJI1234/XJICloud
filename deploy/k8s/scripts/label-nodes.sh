#!/usr/bin/env bash
# 在 K3s Server (133) 上为节点打 role 标签
set -euo pipefail

export KUBECONFIG="${KUBECONFIG:-/etc/rancher/k3s/k3s.yaml}"

label_node() {
  local ip="$1"
  shift
  local labels=("$@")
  local name
  name="$(kubectl get nodes -o jsonpath="{range .items[*]}{.metadata.name}{'\t'}{.status.addresses[?(@.type=='InternalIP')].address}{'\n'}{end}" | awk -v ip="${ip}" '$2==ip {print $1}')"
  if [[ -z "${name}" ]]; then
    echo "[label] Node with IP ${ip} not found, skipping"
    return 0
  fi
  for kv in "${labels[@]}"; do
    kubectl label node "${name}" "${kv}" --overwrite
  done
  echo "[label] ${name} (${ip}) -> ${labels[*]}"
}

label_node "192.168.203.133" "xjicloud.io/role-edge=true"
label_node "192.168.203.134" "xjicloud.io/role-backend=true"
label_node "192.168.203.135" "xjicloud.io/role-storage=true"

# 单节点 bootstrap：若仅 133 在线，为其打上全部角色
if ! kubectl get nodes -o jsonpath='{.items[?(@.status.addresses[?(@.address=="192.168.203.134")])].metadata.name}' 2>/dev/null | grep -q .; then
  echo "[label] Agent 134 未加入，在 133 上启用全部角色（单节点模式）"
  NODE133="$(kubectl get nodes -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.status.addresses[?(@.type=="InternalIP")].address}{"\n"}{end}' | awk '$2=="192.168.203.133" {print $1}')"
  if [[ -n "${NODE133}" ]]; then
    kubectl label node "${NODE133}" \
      xjicloud.io/role-edge=true \
      xjicloud.io/role-backend=true \
      xjicloud.io/role-storage=true \
      --overwrite
  fi
fi

kubectl get nodes --show-labels
