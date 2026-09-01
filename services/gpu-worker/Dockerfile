FROM alibaba-cloud-linux-3-registry.cn-hangzhou.cr.aliyuncs.com/alinux3/alinux3:latest

RUN yum install -y python3 python3-pip curl && yum clean all

WORKDIR /opt/xjicloud-worker

COPY requirements.txt .
# Alinux3 默认 pip 源较旧；使用阿里云 PyPI 镜像并升级 pip
RUN pip3 install --upgrade pip \
    && pip3 install --no-cache-dir -i https://mirrors.aliyun.com/pypi/simple/ -r requirements.txt

COPY worker_agent.py mock_trainer.py oss_client.py entrypoint.sh ./
RUN chmod +x entrypoint.sh

ENV XJICLOUD_BACKEND_URL=http://backend:8080 \
    WORKER_NAME=xjicloud-gpu-worker

# WORKER_SECRET 请在 docker run -e 或 CCI 环境变量中注入，勿硬编码
ENTRYPOINT ["./entrypoint.sh"]
