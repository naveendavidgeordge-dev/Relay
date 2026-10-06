# ADR-0004: AI providers behind a Python sidecar

- **Status:** accepted
- **Context:** "Keep the AI provider integration behind an adapter so it can be mocked in engine
  tests." Free-tier and local providers (Gemini, Groq, Ollama) have the best SDK support in Python,
  and each changes its request shape and usage reporting often.
- **Decision:** A FastAPI sidecar exposes one contract, `POST /classify {prompt, output_schema,
  repair_feedback?} → {output, raw_text, tokens_prompt, tokens_completion, provider, model,
  validation}`, with adapters for gemini, groq, ollama, any OpenAI-compatible endpoint, and a
  deterministic mock. The Java engine talks to it through `AiModelPort` (`AiSidecarClient`:
  WebClient, timeout, circuit breaker), and tests substitute an in-memory fake.
- **Non-decisions:** The sidecar is **not** trusted for correctness. The engine re-validates every
  answer against the snapshot's schema and owns the repair loop and token accounting. Provider keys
  stay in the sidecar's environment and never reach the database, logs or responses.
- **Consequences:** One more container (~60 MB). A provider swap is an env change. The mock provider
  keeps the full demo and the NL eval runnable offline.
