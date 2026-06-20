# Local Infrastructure Stack (insurance-rag-infra)

This directory manages the localized containerized infrastructure required to run the Secure RAG and Ingestion pipeline completely offline on your MacBook.

The stack is optimized for Apple Silicon (ARM64) architectures and mocks AWS S3 storage, Postgres pgvector database layers, and Microsoft Presidio PII/PHI redaction engines natively.

## Container Stack Overview & Port Mappings

| Service Name | Port | Base Image | Purpose |
| :--- | :--- | :--- | :--- |
| `rag-postgres` | `5432` | `pgvector/pgvector:pg16` | Relational vector database |
| `presidio-analyzer` | `5002` | `mcr.microsoft.com/presidio-analyzer` | Named Entity Recognition for PII/PHI |
| `presidio-anonymizer` | `5001` | `mcr.microsoft.com/presidio-anonymizer` | Logic mapper to mask/replace detected PII |
| `localstack-s3` | `4566` | `localstack/localstack:3.8.0` | S3 API Mocking Engine |

---

## Infrastructure Commands

    # 1. Start the stack in the background
    docker compose up -d

    # 2. Check running container status and health
    docker ps

    # 3. View logs of a specific service
    docker compose logs -f localstack
    docker compose logs -f postgres

    # 4. Stop and remove the containers (persisting DB volumes)
    docker compose down

    # 5. Stop the containers and wipe database volumes (Full Reset)
    docker compose down -v

---

## Initial S3 Storage Setup

Once the containers are active and showing as healthy (`docker ps`), you must run the following initialization scripts to construct your local S3 bucket architecture before booting up the Spring Boot backend:

    # 1. Inject mock credentials into your session
    export AWS_ACCESS_KEY_ID="mock"
    export AWS_SECRET_ACCESS_KEY="mock"
    export AWS_DEFAULT_REGION="us-east-1"

    # 2. Create the raw claims ingestion bucket
    aws --endpoint-url=http://localhost:4566 s3 mb s3://raw-claims

    # 3. Create the sanitized claims destination bucket
    aws --endpoint-url=http://localhost:4566 s3 mb s3://sanitized-claims

    # 4. List S3 buckets to verify successful creation
    aws --endpoint-url=http://localhost:4566 s3 ls

---

## Troubleshooting Guide

### 1. LocalStack License Key Validation Failures
* **Error**: `License activation failed! LocalStack cloud disabled...`
* **Root Cause**: Newer versions of LocalStack (using `latest` or `4.x` tags) enforce API key/auth validations.
* **Fix**: Your `docker-compose.yml` has been pinned to `localstack/localstack:3.8.0`. This version operates completely offline with zero API key dependencies. Do not upgrade this tag in development.

### 2. Postgres Port Conflicts (5432)
* **Error**: `Bind for 0.0.0.0:5432 failed: port is already allocated`
* **Root Cause**: You have a local instance of PostgreSQL running natively on your Mac (installed via Homebrew or system installer).
* **Fix**: Either stop your local Mac Postgres daemon (`brew services stop postgresql`) or edit the `docker-compose.yml` port mapping to bind the host port to an alternate port, such as `5433:5432`.

### 3. Presidio Pattern Recognition Failures
* **Error**: Fictional SSNs like `123-45-6789` are not being anonymized.
* **Root Cause**: Presidio executes standard SSA range validations. Fictional test numbers or numbers with invalid prefixes (like `000` or `666`) are intentionally ignored by the model to prevent false positive flags.
* **Fix**: Ensure your mock testing files use a structurally valid synthetic range prefix (e.g., `457-55-5462`) to verify your database's Human-in-the-Loop review routing.