#!/usr/bin/env bash
# 在 192.168.203.134 / 192.168.203.135 上安装 K3s Agent
set -euo pipefail

K3S_URL="${K3S_URL:-https://192.168.203.133:6443}"
K3S_TOKEN="${K3S_TOKEN:?Set K3S_TOKEN from server node-token}"
INSTALL_K3S_MIRROR="${INSTALL_K3S_MIRROR:-}"

echo "[k3s] Joining agent to ${K3S_URL}..."
if [[ -n "${INSTALL_K3S_MIRROR}" ]]; then
  curl -sfL https://rancher-mirror.rancher.cn/k3s/k3s.sh | INSTALL_K3S_MIRROR="${INSTALL_K3S_MIRROR}" K3S_URL="${K3S_URL}" K3S_TOKEN="${K3S_TOKEN}" sh -s - agent
else
  curl -sfL https://get.k3s.io | K3S_URL="${K3S_URL}" K3S_TOKEN="${K3S_TOKEN}" sh -s - agent
fi

echo "[k3s] Agent installed."
