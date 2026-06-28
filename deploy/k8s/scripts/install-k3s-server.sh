#!/usr/bin/env bash
# 在本机 192.168.203.133 上安装 K3s Server
set -euo pipefail

K3S_SERVER_IP="${K3S_SERVER_IP:-192.168.203.133}"
K3S_USE_DOCKER="${K3S_USE_DOCKER:-true}"
INSTALL_K3S_MIRROR="${INSTALL_K3S_MIRROR:-}"
K3S_VERSION="${K3S_VERSION:-v1.30.5+k3s1}"

EXTRA_ARGS=(--tls-san="${K3S_SERVER_IP}" --write-kubeconfig-mode=644)
if [[ "${K3S_USE_DOCKER}" == "true" ]]; then
  EXTRA_ARGS+=(--docker)
fi

if [[ ! -x /usr/local/bin/k3s ]] && [[ ! -f /tmp/k3s ]]; then
  echo "[k3s] Downloading binary ${K3S_VERSION}..."
  curl -fL -o /tmp/k3s "https://github.com/k3s-io/k3s/releases/download/${K3S_VERSION//+/%2B}/k3s"
  chmod +x /tmp/k3s
fi
if [[ -f /tmp/k3s ]] && [[ ! -x /usr/local/bin/k3s ]]; then
  sudo install -m 755 /tmp/k3s /usr/local/bin/k3s
fi

echo "[k3s] Installing server on ${K3S_SERVER_IP} (docker=${K3S_USE_DOCKER})..."
if [[ -n "${INSTALL_K3S_MIRROR}" ]]; then
  curl -sfL https://rancher-mirror.rancher.cn/k3s/k3s.sh | INSTALL_K3S_MIRROR="${INSTALL_K3S_MIRROR}" INSTALL_K3S_SKIP_DOWNLOAD=true sh -s - server "${EXTRA_ARGS[@]}"
else
  curl -sfL https://get.k3s.io | INSTALL_K3S_SKIP_DOWNLOAD=true sh -s - server "${EXTRA_ARGS[@]}"
fi

echo ""
echo "[k3s] Server ready. Join token (run on 134/135 agents):"
sudo cat /var/lib/rancher/k3s/server/node-token
echo ""
echo "Agent install command:"
echo "  curl -sfL https://get.k3s.io | K3S_URL=https://${K3S_SERVER_IP}:6443 K3S_TOKEN=<token> sh -s - agent"
echo ""
echo "kubectl: export KUBECONFIG=/etc/rancher/k3s/k3s.yaml"
