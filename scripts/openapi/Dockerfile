FROM python:3.12-slim

WORKDIR /app

# 先拷贝依赖文件以利用缓存
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

# 再拷贝脚本
COPY gen_openapi_pg16.py ./

# 默认命令可被 compose 覆盖
CMD ["python", "gen_openapi_pg16.py", "--help"]