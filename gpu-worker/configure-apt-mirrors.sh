#!/bin/bash
# Use domestic Ubuntu apt mirrors; drop NVIDIA apt repo (CUDA is preinstalled in base image).
set -euo pipefail

UBUNTU_MIRROR="${UBUNTU_MIRROR:-https://mirrors.aliyun.com/ubuntu}"
APT_BUILD_DNS="${APT_BUILD_DNS:-223.5.5.5 119.29.29.29}"

fix_container_dns() {
    local dns
    if getent hosts mirrors.aliyun.com >/dev/null 2>&1; then
        return 0
    fi
    echo "Container DNS lookup failed; applying build-time DNS override..."
    {
        for dns in ${APT_BUILD_DNS}; do
            echo "nameserver ${dns}"
        done
    } >/etc/resolv.conf
    if getent hosts mirrors.aliyun.com >/dev/null 2>&1; then
        echo "  DNS fixed."
        return 0
    fi
    echo "  DNS still failing. Try: docker build --network=host ..."
    return 1
}

fix_container_dns

echo "Configuring apt mirrors..."
echo "  Ubuntu: ${UBUNTU_MIRROR}"

if [ -f /etc/apt/sources.list.d/ubuntu.sources ]; then
    sed -i "s|http://archive.ubuntu.com/ubuntu|${UBUNTU_MIRROR}|g" \
        /etc/apt/sources.list.d/ubuntu.sources
    sed -i "s|http://security.ubuntu.com/ubuntu|${UBUNTU_MIRROR}|g" \
        /etc/apt/sources.list.d/ubuntu.sources
    sed -i "s|https://archive.ubuntu.com/ubuntu|${UBUNTU_MIRROR}|g" \
        /etc/apt/sources.list.d/ubuntu.sources
    sed -i "s|https://security.ubuntu.com/ubuntu|${UBUNTU_MIRROR}|g" \
        /etc/apt/sources.list.d/ubuntu.sources
fi

if [ -f /etc/apt/sources.list ]; then
    sed -i "s|http://archive.ubuntu.com/ubuntu|${UBUNTU_MIRROR}|g" /etc/apt/sources.list
    sed -i "s|http://security.ubuntu.com/ubuntu|${UBUNTU_MIRROR}|g" /etc/apt/sources.list
    sed -i "s|https://archive.ubuntu.com/ubuntu|${UBUNTU_MIRROR}|g" /etc/apt/sources.list
    sed -i "s|https://security.ubuntu.com/ubuntu|${UBUNTU_MIRROR}|g" /etc/apt/sources.list
fi

for list_file in /etc/apt/sources.list.d/*.list; do
    [ -f "$list_file" ] || continue
    case "$list_file" in
        *cuda*|*nvidia*)
            echo "  Removing ${list_file} (CUDA preinstalled in nvidia/cuda base image)"
            rm -f "$list_file"
            ;;
        *)
            sed -i "s|http://archive.ubuntu.com/ubuntu|${UBUNTU_MIRROR}|g" "$list_file"
            sed -i "s|http://security.ubuntu.com/ubuntu|${UBUNTU_MIRROR}|g" "$list_file"
            sed -i "s|https://archive.ubuntu.com/ubuntu|${UBUNTU_MIRROR}|g" "$list_file"
            sed -i "s|https://security.ubuntu.com/ubuntu|${UBUNTU_MIRROR}|g" "$list_file"
            ;;
    esac
done
