# ECI 同 VPC 拉基座用 -vpc 域名；本机构建若无 VPC 可达，可先：
#   docker tag <公网同镜像>:3.12 <本行地址>
FROM crpi-urdof1biefqsi8uj-vpc.cn-hangzhou.personal.cr.aliyuncs.com/ylif4/python_mirror:3.12

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY run.py .
CMD ["python3", "/app/run.py"]
