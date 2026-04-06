FROM python:3.11-slim

WORKDIR /app

# requirements.txt 설치
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 스크립트 복사
COPY *.py .
COPY data ./data

# 실행 스크립트
COPY run_all.sh .
RUN chmod +x run_all.sh

# 기본 명령: 모든 스크립트 순차 실행
CMD ["./run_all.sh"]
