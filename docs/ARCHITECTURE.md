# Deep Technical Architecture Reference

This document serves as the comprehensive technical reference for the internal architecture, integration configurations, and deployment strategies of the Secure RAG and Ingestion Service.

## 1. System Structure

    insurance-rag/
    ├── infrastructure/                          # Docker-based local infrastructure
    │   └── docker-compose.yml                   # Container stack configuration
    └── backend/                                 # Spring Boot 3.x Backend orchestrator
        ├── pom.xml                              # Project-level dependencies
        ├── local-settings.xml                   # Maven central override settings
        ├── src/
        │   ├── main/
        │   │   ├── java/com/example/insurancerag/
        │   │   │   ├── InsuranceRagApplication.java # App Launcher
        │   │   │   ├── AIConfig.java                # AI Models Beans (Ollama)
        │   │   │   ├── S3Config.java                # S3 Connection Bean (LocalStack)
        │   │   │   ├── RedactionService.java        # Presidio API Handler
        │   │   │   ├── IngestionOrchestrator.java   # Compliance Pipeline Flow
        │   │   │   ├── IngestionController.java     # Redaction REST API
        │   │   │   ├── PolicyIndexingService.java   # Document Parsing Engine
        │   │   │   ├── IndexingController.java      # Indexing REST API
        │   │   │   ├── RetrievalQueryService.java   # pgvector Match & Prompt assembler
        │   │   │   └── RAGController.java           # RAG Query REST API
        │   │   └── resources/
        │   │       ├── application.properties   # Database/Credential configs
        │   │       └── schema.sql               # Automated Postgres Schema

## 2. Module Organisation Rationale
The application is structured monolithically but is strictly organized into independent, domain-driven concerns to allow seamless microservice extraction in the future:
* **`com.example.insurancerag` (Config)**: Houses `S3Config.java` and `AIConfig.java`. Isolates infrastructure concerns so database, S3, or LLM providers can be swapped globally with zero impact on logical workflows.
* **Redaction Domain**: Runs on CPU-bound containers (Microsoft Presidio) [1.1.2] to clean inputs before storing.
* **Vectorizing Domain**: Interacts with local Metal-accelerated inference APIs (Ollama) [11], isolating mathematical mapping and processing away from transactional CRUD layers.
* **JDBC Engine**: Implemented via Spring `JdbcTemplate` to execute optimized pgvector cosine similarity calculations natively, preventing heavy ORM (Hibernate) overhead and abstracting vectorized SQL scripts [23].

## 3. Configuration & Secret Architecture

                             ┌───────────────────────┐
                             │   HashiCorp Vault     │
                             │ (Production Secrets)  │
                             └───────────┬───────────┘
                                         │ (Fetches API keys & credentials)
                                         ▼
                                   [Secure S3 / DB]

The application dynamically reads property keys for database access, S3 credentials, and internal endpoints through environment variables [11]. In production, values are injected securely through external configuration managers (e.g., Vault or AWS Secrets Manager) and are never stored in plain text.

---

### Step 4: LocalStack S3 Storage Integration
For localized development, S3 bucket endpoints are redirected to LocalStack [1.1.2]. 
Our Spring Boot `S3Config` leverages the standard Amazon SDK v2 AWS credentials and region overrides to interact with S3. All files uploaded to `raw-claims` undergo active filtering [1.1.2].

---

### Step 5: pgvector and Local Retrieval Integration

The database layer runs native pgvector operations [23]. To execute a semantic search, the orchestrator:
1. Queries the local Ollama API to vectorize the user's input query using `nomic-embed-text` (768 dimensions) [11].
2. Runs a cosine distance search (`<=>` operator) against the HNSW-indexed vector space in `policy_chunks` [23].
3. Retrieves both the relevant clause (Parent) and maps any associated children chunks (disclaimers/limitations) using explicit UUID cross-referencing.

---

### Step 6: System Prompts and Guardrails
To reduce LLM hallucinations, the system uses a strict grounding template:

    You are an expert insurance claim validation AI. Your task is to provide strict, compliant answers
    based solely on the policy document fragments provided in the Context below.

    Strict Operational Rules:
    1. If the context does not contain the answer, state "I cannot answer this based on the active policy."
    2. Do not assume or extrapolate policies.
    3. Group your response into two sections:
       - Coverage Assessment
       - Associated Limitations and Disclaimers
    4. You must cite the sources using the syntax [Index] where applicable.

This forces the model to stay grounded and only output statements directly supported by the retrieved context.
