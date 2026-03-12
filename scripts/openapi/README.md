```
python gen_openapi_pg16.py `
    --dsn "postgresql://kids_user:kids_pass@localhost:5432/kids_postgres_db" `
    --schemas public `
    --base-path /v1 `
    --title "KG CCTV Platform API" `
    --version "0.1.0" `
    --server-url "http://localhost:8080/api" `
    --out openapi.yaml `
    --update-exclude-suffixes created_at,created_by `
    --create-exclude-suffixes created_at,updated_at
```

```
docker compose up -d
docker compose down --rmi local
```