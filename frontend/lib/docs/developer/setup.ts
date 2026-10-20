import type { DocPage } from "../types";

export const setup: DocPage = {
  slug: "setup",
  group: "Setup & hosting",
  title: "Local setup",
  summary: "Install prerequisites and run the stack — either all in Docker or as local processes.",
  blocks: [
    { t: "h2", text: "Prerequisites" },
    {
      t: "table",
      head: ["Tool", "Version", "Used for"],
      rows: [
        ["JDK", "21 (Temurin)", "Backend + worker"],
        ["Gradle", "8.10+ (or the wrapper)", "Backend build"],
        ["Node.js", "20 LTS or newer", "Console"],
        ["Python", "3.11", "AI sidecar, mock world, smoke test"],
        ["Docker + Compose v2", "24+", "Full stack, Postgres, Testcontainers"],
        ["PostgreSQL", "16 (via Docker)", "Everything durable"],
      ],
    },
    {
      t: "note",
      tone: "info",
      title: "Windows",
      text: "The Makefile expects a POSIX shell. Run `make` from Git Bash or WSL; the Docker commands themselves work from any shell.",
    },
    { t: "h2", text: "Step 1 — get the code and the env file" },
    {
      t: "code",
      lang: "bash",
      code: `git clone <repo-url> relay
cd relay
cp .env.example .env          # every variable is documented inside the file`,
    },
    { t: "h2", text: "Option A — everything in Docker (recommended first run)" },
    {
      t: "code",
      lang: "bash",
      code: `make up        # builds and starts postgres, mock-world, ai-sidecar, backend (api), worker, frontend
make ps        # health of every service
make smoke     # the official contract smoke test
# open http://localhost:3000`,
    },
    {
      t: "p",
      text: "Seed workflows from `data/seed_workflows.json` are loaded as **published** when the API starts, so the console has something to trigger straight away.",
    },
    { t: "h2", text: "Option B — local processes (fastest dev loop)" },
    {
      t: "p",
      text: "Run only Postgres in Docker and everything else on your machine so you get hot reload and debuggers. Use one terminal per process:",
    },
    {
      t: "code",
      lang: "bash",
      code: `make db        # postgres only, in Docker
make world     # python scripts/mock_world.py --port 9210
make sidecar   # uvicorn on :8090 (AI_PROVIDER=mock by default)
make api       # Spring Boot, profile api, :8080
make worker    # Spring Boot, profile worker (a separate JVM, no HTTP)
make web       # Next.js dev server on :3000`,
    },
    {
      t: "note",
      tone: "tip",
      title: "First-time Gradle wrapper",
      text: "The wrapper jar is not committed. Generate it once with `cd backend && gradle wrapper --gradle-version 8.10.2`.",
    },
    { t: "h2", text: "Frontend only" },
    {
      t: "p",
      text: "If you are just working on the console, you do not need the backend for the documentation pages — `/docs` and `/guide` are fully static. Pages that list workflows or runs will show an “API unreachable” state until the backend is up.",
    },
    {
      t: "code",
      lang: "bash",
      code: `npm install                           # from the repo root (npm workspaces)
npm --workspace frontend run dev      # http://localhost:3000
# optional, point the console at an API that is not on localhost:8080:
RELAY_API_URL=http://localhost:8080 RELAY_API_TOKEN=relay-demo-token npm --workspace frontend run dev`,
    },
    { t: "h2", text: "Useful make targets" },
    {
      t: "table",
      head: ["Target", "Purpose"],
      rows: [
        [
          "`make up` / `down` / `logs` / `ps`",
          "Manage the full Docker stack. `down` keeps the database volume.",
        ],
        ["`make test`", "Run backend, frontend and sidecar suites."],
        ["`make smoke`", "Run `scripts/smoke_test.py` against a running stack."],
        ["`make drill`", "Kill-and-resume drill plus `duplication_check.py`."],
        ["`make nl-eval`", "Score the NL compiler against `data/nl_eval.jsonl`."],
        ["`make fmt` / `make lint`", "Apply formatters / run static checks across all languages."],
        ["`make validate-pack` / `make hashes`", "Verify the read-only fixtures are intact."],
        ["`make clean`", "Remove build outputs."],
      ],
    },
  ],
};

export const configuration: DocPage = {
  slug: "configuration",
  group: "Setup & hosting",
  title: "Configuration",
  summary: "Every environment variable that matters, with defaults and what it changes.",
  blocks: [
    {
      t: "p",
      text: "Configuration is by environment variables, loaded from `.env` (copied from `.env.example`) by Docker Compose and by the Makefile. **Never commit a real `.env`.**",
    },
    { t: "h2", text: "Database" },
    {
      t: "table",
      head: ["Variable", "Default", "Meaning"],
      rows: [
        [
          "`POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD`",
          "`relay` / `relay` / `relay`",
          "Used to create the Postgres container.",
        ],
        [
          "`DATABASE_URL`",
          "`jdbc:postgresql://localhost:5432/relay`",
          "JDBC URL for the backend and worker. In Compose it is `postgres:5432`.",
        ],
        [
          "`DATABASE_USER` / `DATABASE_PASSWORD`",
          "`relay` / `relay`",
          "Credentials the application connects with.",
        ],
      ],
    },
    { t: "h2", text: "Platform authentication" },
    {
      t: "table",
      head: ["Variable", "Default", "Meaning"],
      rows: [
        [
          "`RELAY_DEMO_TOKEN`",
          "`relay-demo-token`",
          "Bearer token accepted on every platform API (builder + operator + approver).",
        ],
        [
          "`RELAY_JWT_PUBLIC_KEY`",
          "empty",
          "PEM RSA public key (single line, `\\n` escaped). When set, RS256 JWTs with a `roles` claim are also accepted.",
        ],
        ["`RELAY_JWT_ISSUER`", "`relay`", "Expected `iss` for JWTs."],
        [
          "`RELAY_API_URL` (frontend)",
          "`http://localhost:8080`",
          "Where the console’s server reaches the API.",
        ],
        [
          "`RELAY_API_TOKEN` (frontend)",
          "`relay-demo-token`",
          "Token the console’s server sends. It never reaches the browser.",
        ],
      ],
    },
    { t: "h2", text: "Process role and worker tuning" },
    {
      t: "table",
      head: ["Variable", "Default", "Meaning"],
      rows: [
        ["`WORKER_ONLY`", "`false`", "`true` starts the image as a worker with no HTTP server."],
        [
          "`RELAY_WORKER_ID`",
          "unset",
          "Stable identity. A restarted worker with the same id releases its previous leases at once.",
        ],
        ["`RELAY_WORKER_CONCURRENCY`", "`4`", "Runs one worker advances in parallel."],
        [
          "`RELAY_LEASE_SECONDS`",
          "`15`",
          "Queue-job lease length; a heartbeat renews it every lease/3.",
        ],
        ["`RELAY_POLL_INTERVAL_MS`", "`250`", "How often an idle worker polls the queue."],
      ],
    },
    { t: "h2", text: "The outside world and AI" },
    {
      t: "table",
      head: ["Variable", "Default", "Meaning"],
      rows: [
        [
          "`MOCK_WORLD_URL`",
          "`http://localhost:9210`",
          "Base URL for `notify` and `order_action`.",
        ],
        [
          "`RELAY_URL_ALIASES`",
          "empty",
          "Comma-separated `from=>to` prefix rewrites applied to `http_request` URLs at dispatch time. In Docker: `http://localhost:9210=>http://mock-world:9210`. Stored definitions are never changed.",
        ],
        [
          "`RELAY_HTTP_ALLOWED_HOSTS`",
          "empty (any)",
          "SSRF allow-list of hosts for `http_request`.",
        ],
        [
          "`RELAY_HTTP_CONNECT_TIMEOUT_MS` / `RELAY_HTTP_READ_TIMEOUT_MS`",
          "`2000` / `10000`",
          "Outbound HTTP timeouts.",
        ],
        ["`AI_SIDECAR_URL`", "`http://localhost:8090`", "Where the engine sends `ai` node calls."],
        ["`AI_PROVIDER`", "`mock`", "Sidecar adapter: `mock`, `gemini`, `groq`, `ollama`."],
        ["`AI_MODEL`", "empty", "Optional model override."],
        [
          "`GEMINI_API_KEY` / `GROQ_API_KEY` / `OLLAMA_URL`",
          "empty",
          "Provider credentials. Never logged, never returned.",
        ],
        ["`AI_LOG_PROMPTS`", "`false`", "Only when `true` are full prompts logged."],
        [
          "`MOCK_AI_FIRST_INVALID`",
          "`false`",
          "Makes the mock model answer badly once, to demonstrate the repair retry.",
        ],
        ["`RELAY_COMPILER_MODE`", "`heuristic`", "NL compiler strategy: `heuristic` or `llm`."],
      ],
    },
    { t: "h2", text: "Retries" },
    {
      t: "table",
      head: ["Variable", "Default", "Meaning"],
      rows: [
        ["`RELAY_RETRY_MAX_ATTEMPTS`", "`5`", "Attempts for transient failures."],
        ["`RELAY_RETRY_INITIAL_INTERVAL_MS`", "`500`", "First backoff."],
        ["`RELAY_RETRY_MULTIPLIER`", "`2.0`", "Backoff multiplier (with jitter)."],
      ],
    },
    { t: "h2", text: "Cryptography" },
    {
      t: "table",
      head: ["Variable", "Default", "Meaning"],
      rows: [
        [
          "`RELAY_MASTER_KEY`",
          "dev key",
          "32-byte base64 key-encryption key wrapping the ML-KEM / X25519 / ML-DSA private keys.",
        ],
        [
          "`RELAY_DB_COLUMN_KEY`",
          "dev key",
          "pgcrypto `pgp_sym_encrypt` key for defence-in-depth column encryption.",
        ],
        [
          "`RELAY_PQ_PROVIDER`",
          "`bouncycastle`",
          "`bouncycastle` for real use, `test` (deterministic, **insecure**) for tests only.",
        ],
      ],
    },
    {
      t: "note",
      tone: "warn",
      title: "Before you host anywhere real",
      text: "Generate your own `RELAY_MASTER_KEY` (`openssl rand -base64 32`) and `RELAY_DB_COLUMN_KEY`, and change `RELAY_DEMO_TOKEN` and the Postgres password. The defaults are for local development only.",
    },
  ],
};

export const docker: DocPage = {
  slug: "docker",
  group: "Setup & hosting",
  title: "Docker & hosting",
  summary: "The Compose stack service by service, the images, TLS, and a production checklist.",
  blocks: [
    {
      t: "p",
      text: "The whole system is described by `docker/docker-compose.yml` (project name `relay`). It is started with the root `Makefile`, which wraps `docker compose -f docker/docker-compose.yml --env-file .env`.",
    },
    {
      t: "code",
      lang: "bash",
      title: "Start, inspect, stop",
      code: `cp .env.example .env
docker compose -f docker/docker-compose.yml --env-file .env up -d --build
docker compose -f docker/docker-compose.yml ps
docker compose -f docker/docker-compose.yml logs -f --tail=100
docker compose -f docker/docker-compose.yml down        # keeps the pgdata volume
# or simply: make up | make ps | make logs | make down`,
    },
    { t: "h2", text: "Services" },
    {
      t: "table",
      head: ["Service", "Container", "Port", "Notes"],
      rows: [
        [
          "`postgres`",
          "`relay-postgres`",
          "5432",
          "`postgres:16-alpine`; data in the `pgdata` volume; `pg_isready` healthcheck.",
        ],
        [
          "`mock-world`",
          "`relay-mock-world`",
          "9210",
          "`python:3.11-slim` running `scripts/mock_world.py`, mounted read-only; health at `/health`.",
        ],
        [
          "`ai-sidecar`",
          "`relay-ai-sidecar`",
          "8090",
          "Built from `docker/ai-sidecar.Dockerfile`; provider chosen by `AI_PROVIDER`.",
        ],
        [
          "`backend`",
          "`relay-api`",
          "8080",
          "Image `relay-backend:local`, `SPRING_PROFILES_ACTIVE=api`. Starts after Postgres and the mock world are healthy.",
        ],
        [
          "`worker`",
          "`relay-worker`",
          "—",
          'Same image, `WORKER_ONLY=true`, profile `worker`, `RELAY_WORKER_ID=worker-1`, `pull_policy: never`, `restart: "no"` (the drill restarts it deliberately).',
        ],
        [
          "`frontend`",
          "`relay-console`",
          "3000",
          "Next.js standalone server. `RELAY_API_URL=http://backend:8080`, token from `RELAY_DEMO_TOKEN`.",
        ],
        [
          "`nginx`",
          "`relay-tls`",
          "8443",
          "Only with `--profile tls`. TLS 1.3 front door for backend and frontend.",
        ],
      ],
    },
    {
      t: "p",
      text: "Environment shared by `backend` and `worker` is defined once as a YAML anchor (`x-backend-env`). Inside Compose the services find each other by service name (`postgres`, `mock-world`, `ai-sidecar`, `backend`), and `RELAY_URL_ALIASES` rewrites the seeds’ `http://localhost:9210` to `http://mock-world:9210` at dispatch time.",
    },
    { t: "h2", text: "The frontend image" },
    {
      t: "p",
      text: "`docker/frontend.Dockerfile` is a three-stage build with the **repository root** as the build context (because of npm workspaces):",
    },
    {
      t: "ol",
      items: [
        "**deps** — `node:20-alpine`, copy `package.json`, `package-lock.json` and `frontend/package.json`, run `npm ci --workspace frontend --include-workspace-root`.",
        '**build** — copy sources and run `npm --workspace frontend run build`. `next.config.mjs` sets `output: "standalone"` and traces from the repo root so the bundle contains the hoisted `node_modules`.',
        "**runtime** — a fresh `node:20-alpine`, a non-root `relay` user, copy `.next/standalone`, `.next/static` and `public`, then `node frontend/server.js` on port 3000.",
      ],
    },
    {
      t: "note",
      tone: "info",
      title: "Documentation pages and Docker",
      text: "The `/docs` and `/guide` pages are generated at build time from TypeScript content (`generateStaticParams`), so they ship inside the standalone bundle and need no extra container, volume or API.",
    },
    { t: "h2", text: "Optional TLS front door" },
    {
      t: "code",
      lang: "bash",
      code: `bash docker/nginx/gen-certs.sh                       # create local certificates first
docker compose -f docker/docker-compose.yml --env-file .env --profile tls up -d --build
# https://localhost:8443`,
    },
    {
      t: "p",
      text: "nginx terminates TLS 1.3 only (no session tickets, HSTS enabled). Inside the Compose network services speak plain HTTP.",
    },
    { t: "h2", text: "Production hosting checklist" },
    {
      t: "ul",
      items: [
        "**Secrets:** set unique `RELAY_MASTER_KEY`, `RELAY_DB_COLUMN_KEY`, `RELAY_DEMO_TOKEN` (or switch to JWTs with `RELAY_JWT_PUBLIC_KEY`) and a strong Postgres password. Inject them from a secret manager, not from a committed file.",
        "**Database:** use a managed or backed-up Postgres 16. Do not publish port 5432 to the internet. Flyway runs migrations on API start.",
        "**Scale:** run one `backend` (API) and as many `worker` replicas as you need — leases and `SKIP LOCKED` make multiple workers safe by construction. Give each a stable `RELAY_WORKER_ID`.",
        "**Network:** only expose the console (3000) or the nginx TLS port. The API and Postgres can stay on the private network because the console reaches the API server-side.",
        "**AI:** set `AI_PROVIDER` and its key on the sidecar only; keep `AI_LOG_PROMPTS=false`.",
        "**Health:** the backend exposes `/actuator/health` (public) for probes and `/actuator/prometheus` (authenticated) for metrics. The worker has no HTTP port — its liveness is the queue (leases expire if it stops heart-beating).",
        "**Allow-list:** set `RELAY_HTTP_ALLOWED_HOSTS` so `http_request` nodes can only reach hosts you trust.",
      ],
    },
    {
      t: "note",
      tone: "warn",
      title: "No built-in user login yet",
      text: "The console currently authenticates to the API with one server-side token and has no per-user sign-in. Put it behind your own access control (VPN, reverse-proxy auth) until user authentication is added.",
    },
  ],
};
