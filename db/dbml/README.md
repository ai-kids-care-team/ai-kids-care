```
npm install -g @dbml/cli

dbml2sql db/dbml/schema.dbml -o db/initdb/01_create_schema.sql
```

```
python gen_openapi_pg16.py `
    --dsn "postgresql://kids_user:kids_pass@localhost:5432/kids_postgres_db" `
    --schemas public `
    --base-path /api/v1 `
    --title "KG CCTV Platform API" `
    --version "0.1.0" `
    --server-url "http://localhost:8080" `
    --out openapi.yaml
```