FROM python:3.11-slim

# 长生命周期 sync：关闭 stdout 缓冲，保证 tick / reconcile 日志实时刷出（docker logs 可见）
ENV PYTHONUNBUFFERED=1

WORKDIR /app

# requirements.txt 설치
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 스크립트 복사 (CSV 스냅샷 없음 — 데이터는 PostgreSQL 에서 직접 조회)
COPY *.py .

# 실행 스크립트
COPY run_all.sh .
RUN chmod +x run_all.sh

# 기본 명령: 모든 스크립트 순차 실행
CMD ["./run_all.sh"]
