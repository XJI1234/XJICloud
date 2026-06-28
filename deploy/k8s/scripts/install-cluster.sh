#!/usr/bin/env bash
# 在三台 VM 上远程安装 K3s（需 root SSH 免密到 134/135）
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
K3S_SERVER_IP="${K3S_SERVER_IP:-192.168.203.133}"
AGENT_IPS=(192.168.203.134 192.168.203.135)
INSTALL_K3S_MIRROR="${INSTALL_K3S_MIRROR:-cn}"
SSH_OPTS=(-o StrictHostKeyChecking=accept-new)

if ! command -v k3s >/dev/null 2>&1; then
  echo "[cluster] Installing K3s server on ${K3S_SERVER_IP}..."
  INSTALL_K3S_MIRROR="${INSTALL_K3S_MIRROR}" sudo -E "${SCRIPT_DIR}/install-k3s-server.sh"
fi

export KUBECONFIG="${KUBECONFIG:-/etc/rancher/k3s/k3s.yaml}"
TOKEN="$(sudo cat /var/lib/rancher/k3s/server/node-token)"

for ip in "${AGENT_IPS[@]}"; do
  echo "[cluster] Installing agent on ${ip}..."
  ssh "${SSH_OPTS[@]}" "root@${ip}" "command -v k3s >/dev/null || curl -sfL https://rancher-mirror.rancher.cn/k3s/k3s.sh | INSTALL_K3S_MIRROR=${INSTALL_K3S_MIRROR} K3S_URL=https://${K3S_SERVER_IP}:6443 K3S_TOKEN=${TOKEN} sh -s - agent"
done

echo "[cluster] Labeling nodes..."
sudo "${SCRIPT_DIR}/label-nodes.sh"

echo "[cluster] Nodes:"
kubectl get nodes -o wide
