# AI Kids Care

[한국어](README.md) | **English** | [中文](README.zh-CN.md)

AI Kids Care is an AI-assisted safety management platform for kindergarten environments. It connects CCTV cameras, guardians, teachers, children, notifications, announcements, appreciation letters, and AI-detected safety events into one operational workflow. The repository is organized as a monorepo containing the frontend, backend, database assets, AI inference service, and deployment automation.

## Project Overview

The system is designed so kindergarten operators can manage cameras and classroom data, review AI-detected events, and communicate important information to guardians and staff. The main workflows are:

- Sign-up, login, server-side session authentication (Spring Session + Redis + cookie + CSRF), and role-based menus
- Kindergarten, class, room, child, guardian, and teacher management
- CCTV camera and stream management
- AI detection sessions, detected events, evidence files, and event reviews
- Announcements and appreciation letters
- Child-centered relationship graph backed by Neo4j
- Experimental real-time Pushover and SMS alerts

## Core Features

| Area | Details |
| --- | --- |
| Authentication and roles | `GUARDIAN`, `TEACHER`, `KINDERGARTEN_ADMIN`, `PLATFORM_IT_ADMIN`, `SUPERADMIN`, server-side session authentication (Spring Session + Redis + cookie + CSRF) login/refresh/logout, role-based menus (ADR-0016) |
| Kindergarten operations | Kindergartens, classes, rooms, teachers, guardians, children, class assignments, guardian relationships, room assignments |
| CCTV and events | Cameras, streams, AI models, detection sessions, detection events, reviews, evidence files |
| Communication | Announcements, appreciation letters, notification rules, device tokens, notification history |
| Graph data | Child-centered relationship graph using Neo4j |
| AI inference | VideoMAE-based video classification, path/upload prediction APIs, live stream detection and alert experiments |

## Technical Architecture

| Layer | Technology |
| --- | --- |
| Frontend | Next.js 16, React 19, TypeScript, Tailwind CSS, Radix UI, Redux Toolkit, Axios |
| Backend | Java 21, Spring Boot 3.2.5, Spring Web, Spring Security, Spring Data JPA, Validation, MapStruct, Springdoc OpenAPI, Neo4j Java Driver |
| Database | PostgreSQL 16, Neo4j 5.19, SQL init scripts, seed data, DBML, ERD diagrams |
| AI | Python, FastAPI, Uvicorn, PyTorch, Transformers VideoMAE, AV/FFmpeg, Pushover, SMS |
| DevOps | Docker, Docker Compose, Nginx, Gradle, Jenkinsfile |

## Repository Layout

```text
.
|-- frontend/             # Next.js UI, pages, components, Redux store, API clients
|-- backend/              # Spring Boot API server
|-- ai/                   # VideoMAE training, inference, serving, stream alert scripts
|-- db/                   # PostgreSQL schema, seed data, Neo4j loader, DB utilities
|-- openspec/             # OpenSpec specs (openspec/specs) and change proposals
|-- (pg-spring-crud-codegen/ retired 2026-06-18, ADR-0027)
|-- jenkins/              # Jenkins image and compose helper
|-- docker-compose.yml    # Main stack: PostgreSQL, Neo4j, data loader, backend, frontend
|-- Jenkinsfile           # CI/CD pipeline for compose deployment
`-- README*.md            # Multilingual project documentation
```

## Quick Start

The root `docker-compose.yml` starts PostgreSQL, Neo4j, the Neo4j data loader, the Spring Boot backend, and the Nginx-hosted frontend.

```bash
docker compose up -d --build
```

Main service URLs:

| Service | URL |
| --- | --- |
| Frontend | `http://localhost` |
| Backend API | `http://localhost:8080/api/v1` |
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` |
| Neo4j Browser | `http://localhost:7474` |
| PostgreSQL | `localhost:5432` |

The compose file provides fallback values for local environment variables. For production or shared environments, create `.env` from `.env.example` and explicitly configure the database credentials, Neo4j credentials, and JWT secret.

```bash
cp .env.example .env
docker compose up -d --build
```

## Local Development

You can run only the data services in Docker and start the backend/frontend as local processes.

```bash
docker compose up -d db neo4j data-loader
```

Backend:

```bash
cd backend
./gradlew bootRun
```

Windows PowerShell:

```powershell
cd backend
.\gradlew.bat bootRun
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

The frontend dev server runs at `http://localhost:3000`. Its default API base URL is `http://localhost:8080/api/v1`; override `NEXT_PUBLIC_API_BASE_URL` if needed, using `frontend/.env.example` as a reference.

Useful development commands:

```bash
# frontend
npm run lint
npm run build

# backend
./gradlew test
./gradlew bootJar
```

## AI Service

The AI module can be run separately from the root compose stack. The inference API is served by FastAPI and uses port `8001`.

```bash
cd ai
docker compose up -d --build
```

Local Python execution:

```bash
cd ai
python -m venv .venv
source .venv/bin/activate
pip install uv
uv sync --no-dev
export PYTHONPATH=src
python scripts/serve.py
```

Windows PowerShell:

```powershell
cd ai
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install uv
uv sync --no-dev
$env:PYTHONPATH = "src"
python scripts\serve.py
```

AI service endpoints:

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/health` | Check model directory, device, and labels |
| `POST` | `/predict/path` | Predict from a video path accessible to the server |
| `POST` | `/predict/upload` | Predict from an uploaded video file |

The default `AI_MODEL_DIR` is `outputs/videomae_baseline/best_model`. Both Docker and local execution require real model files at that path. Live stream inference and alert experiments are centered around `ai/scripts/stream_live_alert_service.py`.

## Database and Documentation

- PostgreSQL schema and seed SQL: `db/initdb/`
- DBML schema: `db/dbml/schema.dbml`
- ERD documentation: `openspec/specs/data-platform/spec.md`
- Neo4j data loader: `db/ne4j_kindergartens/` (rebuilds the derived graph one-shot by querying PostgreSQL directly; no CSV)
- Code generation tool: ~~`pg-spring-crud-codegen/`~~ (retired 2026-06-18, ADR-0027; new domain objects are hand-written)

The backend runs with Hibernate `ddl-auto=validate`, so the database schema must exist before the application starts. The root Docker Compose setup applies `db/initdb` when the PostgreSQL container is created.

## Main API Areas

Backend APIs are mounted under `/api/v1`.

- Auth: `/auth/login`, `/auth/logout`, `/auth/refresh`, `/auth/register`, `/auth/password-resets`
- Operations data: `/users`, `/kindergartens`, `/classes`, `/rooms`, `/children`, `/teachers`, `/guardians`
- CCTV and AI events: `/cctv_cameras`, `/camera_streams`, `/ai_models`, `/detection_sessions`, `/detection_events`, `/event_reviews`, `/event_evidence_files`
- Communication: `/announcements`, `/appreciation_letters`, `/notifications`, `/notification_rules`, `/device_tokens`
- Graph: `/graph/children/{childId}`
- Common codes and menus: `/common_codes`, `/menus`

Run the backend and open Swagger UI for detailed request and response schemas.

## Future Development Approach

Starting on `2026-05-11`, future development of this project will make extensive use of Vibe Coding and AI Agents. This choice has two main reasons. First, Vibe Coding technology has advanced quickly and can now materially improve the efficiency of single-person development and maintenance. Second, the project has moved from a three-person collaboration model to one effective maintainer/developer for subsequent work, making AI Agents a necessary development aid.

Commits, features, and contribution statistics before `2026-05-11` should still be understood as work produced under the previous traditional team-development model. This note is included only to distinguish future development practice and to avoid the misunderstanding that the entire project was generated by AI Agents from the beginning.

## Contributions and Roles

The statistics below include only commits before `2026-04-10 00:00:00 +0900`. The last included commit is `0c6dda6` (`2026-04-08 22:14:27 +0900`). Later commits and this README rewrite are not included.

Statistics basis:

- Commit share: based on 407 commits before the cutoff
- Churn share: based on `git numstat` added + deleted lines, 256,644 total lines
- Roles: inferred from pre-cutoff commit messages and directory-level change distribution

| Contributor | Commits | Commit share | Churn | Churn share | Main roles and responsibilities |
| --- | ---: | ---: | ---: | ---: | --- |
| Zhang Junfan 장준범 | 323 | 79.4% | 186,294 | 72.6% | Project lead, architect, and main developer. Led the backend APIs, data model, AI training/inference pipeline, real-time alerts, and Docker/Jenkins setup. |
| korea4050-debug | 63 | 15.5% | 29,716 | 11.6% | Backend/frontend integration, DB and Neo4j setup, seed data, and support for announcements, authentication, and detection-event workflows. |
| deokwoo-han | 21 | 5.2% | 40,634 | 15.8% | Frontend engineer. Focused on the CCTV monitoring dashboard, appreciation-letter screens, frontend page fixes, and small backend integration updates. |
