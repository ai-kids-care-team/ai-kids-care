# 로깅 가이드 (Spring Boot 3.2.x)

백엔드 로그는 **`src/main/resources/logback-spring.xml`** 에서 통합 관리합니다.  
`application.yml` / `application-local.yml` 에는 **`logging.level` 등 로그 관련 설정을 두지 않습니다** (충돌 방지).

---

## 1. 설정 파일

| 파일 | 역할 |
|------|------|
| `backend/src/main/resources/logback-spring.xml` | 콘솔·파일 출력, 롤링, 로거 레벨 |
| `backend/src/main/resources/application.yml` | DB·서버 등만 (로그 레벨 없음) |
| `backend/src/main/resources/application-local.yml` | 로컬 전용 (로그 레벨 없음) |

---

## 2. 로그 패턴

```
%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n
```

날짜 형식: **`yyyy-MM-dd HH:mm:ss.SSS`**

---

## 3. 출력 대상

- **콘솔**: 항상 출력 (프로파일별 `CONSOLE` appender)
- **파일**: 동일 패턴으로 파일에도 기록 (`FILE` appender)
- **root** 로거는 **INFO** 이며, `CONSOLE` + `FILE` 둘 다 연결되어 있습니다.

---

## 4. Spring 프로파일별 로그 경로

애플리케이션 **작업 디렉터리(보통 프로젝트 루트 또는 `backend` 모듈 루트)** 기준 상대 경로입니다.

### `local` 프로파일

| 구분 | 경로 |
|------|------|
| 현재 로그 | `logs_local/app.log` |
| 날짜별 아카이브 | `logs_local/app_local.yyyy-MM-dd.log` (예: `app_local.2026-03-21.log`) |

IntelliJ에서 `SPRING_PROFILES_ACTIVE=local` 로 실행할 때 해당 경로를 사용합니다.

### `dev` 프로파일

| 구분 | 경로 |
|------|------|
| 현재 로그 | `logs_dev/app.log` |
| 날짜별 아카이브 | `logs_dev/app_dev.yyyy-MM-dd.log` |

**Docker 로 백엔드를 띄울 때** 컨테이너 안 작업 디렉터리는 `/app` 입니다.  
`docker-compose.yml` 의 `backend` 서비스에 **`SPRING_PROFILES_ACTIVE=dev`** 가 설정되어 있어야 위 `logs_dev/` 경로가 사용됩니다.  
또한 이미지 빌드 시 **`/app/logs`, `/app/logs_dev`, `/app/logs_local`** 디렉터리를 만들고 `spring` 사용자에게 권한을 줍니다. 그렇지 않으면 `/app` 이 root 소유라 **파일 로그 생성 시 `Failed to create parent directories`** 가 날 수 있습니다.

### `local` 도 아니고 `dev` 도 아닐 때

예: 프로덕션 빌드, `prod` 만 켠 경우 등.

| 구분 | 경로 |
|------|------|
| 현재 로그 | `logs/app.log` |
| 날짜별 아카이브 | `logs/app.yyyy-MM-dd.log` |

---

## 5. 파일 롤링 정책

- **구현**: `RollingFileAppender` + `TimeBasedRollingPolicy`
- **보관 일수**: `maxHistory` = **2** (2일이 지난 일자 로그 파일은 정리 대상)
- **전체 용량 상한**: `totalSizeCap` = **1GB**

---

## 6. 로거 레벨 (요약)

| 로거 | 레벨 | 설명 |
|------|------|------|
| **root** | **INFO** | 애플리케이션 일반 로그 |
| `org.springframework` | **WARN** | Spring 내부 로그 과다 출력 방지 |
| `org.hibernate.SQL` | **DEBUG** | 실행 SQL 문 |
| `org.hibernate.orm.jdbc.bind` | **TRACE** | SQL 바인딩 파라미터 값 (Hibernate 6 계열) |

SQL·바인딩 로그는 **additivity** 로 root 를 타기 때문에 **콘솔과 파일 모두**에 동일하게 남습니다.

`spring.jpa.show-sql` 은 `false` 로 두고, **로그로 SQL을 보는 방식**을 권장합니다 (포맷·레벨을 logback 과 맞추기 쉬움).

---

## 7. `application*.yml` 과의 역할 분리

- 예전에 `application.yml` / `application-local.yml` 에 있던 **`logging.level` 설정은 제거**했습니다.
- 로그 레벨·출력 대상은 **`logback-spring.xml` 한 곳**에서만 조정합니다.
- yml 에는 주석으로만 안내합니다: *로그는 logback-spring.xml 에서 관리*.

---

## 8. Git 에서 제외하는 로그 폴더

`.gitignore` 에 다음이 포함되어 있습니다.

- `logs/`, `log/`
- **`logs*/`** — `logs_local/`, `logs_dev/` 등 **`logs` 로 시작하는 디렉터리** 전체

로그 파일은 저장소에 올리지 않습니다.

---

## 9. 주의사항

1. **`local` 과 `dev` 프로파일을 동시에 활성화**하면, logback 설정상 동일한 appender 이름이 중복될 수 있습니다. 로그 경로를 나누려면 **보통 한 프로파일만** 켜는 것이 안전합니다.
2. 로그 파일 경로는 **실행 시 작업 디렉터리**에 따라 달라질 수 있습니다. IntelliJ 에서는 Run Configuration 의 Working directory 를 확인하세요.
3. 도커에서 **`SPRING_PROFILES_ACTIVE` 없이** 실행하면 `logs/app.log` 폴백 구간이 쓰이는데, 컨테이너 권한 문제로 동일하게 실패할 수 있습니다. **백엔드 컨테이너는 `dev` 프로파일 + 로그 디렉터리 사전 생성**을 사용하세요.

### 트러블슈팅: `Failed to create parent directories` / `logs/app.log (No such file or directory)`

- 원인: (1) `dev` 미설정으로 `logs/` 사용 + (2) `spring` 사용자가 `/app` 아래에 디렉터리를 만들 수 없음.
- 조치: `docker-compose.yml` 에 `SPRING_PROFILES_ACTIVE: dev` 확인, 이미지 **재빌드** (`docker compose build --no-cache backend` 등) 후 다시 기동.

---

## 10. 빠른 확인

- 프로파일 `local` 로 기동 후, 콘솔에 위 패턴으로 로그가 나오는지 확인합니다.
- `logs_local/app.log` 가 생성되는지 확인합니다.
- DB 조회 등이 발생할 때 `org.hibernate.SQL` / 바인딩 TRACE 가 콘솔·파일 모두에 찍히는지 확인합니다.

---

## 관련 문서

- [README_Docker_IntelliJ_동시실행.md](./README_Docker_IntelliJ_동시실행.md) — Docker / IntelliJ 동시 실행 시 포트·환경 변수
