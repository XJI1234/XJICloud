FROM colmap/colmap:latest

ARG USE_CN_APT_MIRROR=1
ARG UBUNTU_MIRROR=https://mirrors.aliyun.com/ubuntu

# Ubuntu 24.04 需处理 PEP 668（externally-managed-environment）
COPY configure-apt-mirrors.sh /usr/local/bin/configure-apt-mirrors.sh
RUN if [ "${USE_CN_APT_MIRROR}" = "1" ]; then \
        UBUNTU_MIRROR="${UBUNTU_MIRROR}" bash /usr/local/bin/configure-apt-mirrors.sh; \
    fi && \
    apt-get update && apt-get install -y --no-install-recommends \
    python3 python3-pip curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt/xjicloud-worker

COPY requirements.txt .
RUN pip3 install --no-cache-dir --break-system-packages \
    -i https://mirrors.aliyun.com/pypi/simple/ -r requirements.txt

COPY worker_agent.py mock_trainer.py oss_client.py entrypoint.sh ./
RUN chmod +x entrypoint.sh

ENV XJICLOUD_BACKEND_URL=http://backend:8080 \
    WORKER_NAME=xjicloud-gpu-worker

# WORKER_SECRET 请在 docker run -e 或 CCI 环境变量中注入，勿硬编码
ENTRYPOINT ["./entrypoint.sh"]
