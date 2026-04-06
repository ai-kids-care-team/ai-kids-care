1.
.venv 만들기

2. python -m pip install -r requirements.txt

docker rm neo4j

폴더 안에 파일 삭제
먼저 도커 삭제 먼저 하고, 실제 데이터 삭제하기

```
cd neo4j
rmdir /s /q data
```

3. 도커를 실행

3-1. 디렉토리 이동
```
cd neo4j
```

3-2.컨테이너 + 볼륨 완전 제거 (데이터 초기화)
- 컨테이너 종료
- 네트워크 제거
- 볼륨 삭제 (⚠️ DB 데이터 완전 삭제)

```
docker compose down -v
```

3-2-1.
```
docker rm neo4j
```

3-2-2.(선택) 남아있는 컨테이너 강제 삭제
```
docker ps -a | grep neo4j
docker rm -f neo4j
```

3-4. 컨테이너 재실행
```
docker compose up -d
```

4. main 실행
neo4j_connect.py 실행

5. 데이터 적재
no10~no100 실행

6. 관계설정(쿼리)

7. backend
https://github.com/mathkogogany1490/sales_project

7-1) api check
- https://github.com/mathkogogany1490/sales_project/blob/main/backend/sales/urls.py

7-2)views.py
 https://github.com/mathkogogany1490/sales_project/blob/main/backend/sales/views.py
CustomerGraphAPIView 함수

7-3) java21  jap 로 controller 로 변환 및 설정


5. url
http://localhost:7474

6.파이썬 코드로 CSV 파일을 가져와서 NEO4J에 데이터를 넣고 관계를 작성한 모듈 10개를 
자바 서버가 실행될 때 자동으로 실행되도록 파이썬 모듈 10개를 자바 코드로 전환하고 
싶습니다. 

파일 10개를 chatGPT에 주고 명령을 내려주세요


