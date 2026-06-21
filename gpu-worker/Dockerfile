FROM alibaba-cloud-linux-3-registry.cn-hangzhou.cr.aliyuncs.com/alinux3/alinux3:latest

RUN yum install -y python3 python3-pip curl && yum clean all

WORKDIR /opt/xjicloud-worker

COPY requirements.txt .
RUN pip3 install --no-cache-dir -r requirements.txt

COPY worker_agent.py mock_trainer.py oss_client.py entrypoint.sh ./
RUN chmod +x entrypoint.sh

ENV XJICLOUD_BACKEND_URL=http://backend:8080 \
    WORKER_SECRET=change-me-worker-secret-in-production \
    WORKER_NAME=xjicloud-gpu-worker

ENTRYPOINT ["./entrypoint.sh"]
