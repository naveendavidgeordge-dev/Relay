# ADR-0002: Postgres is the queue

- **Status:** accepted
- **Context:** Runs must survive restarts, a job taken by a crashed worker must become available
  again, and step persistence must be atomic with run state ("A single relational database is a
  perfectly good answer for everything below, including the queue" — DATA_MODEL).
- **Decision:** `queue_jobs` table, one row per run with work to do (`UNIQUE (run_id)`). Claim with
  `UPDATE … WHERE id IN (SELECT … FOR UPDATE SKIP LOCKED LIMIT n) RETURNING …`. Leases are
  `locked_until` timestamps on the **database** clock, renewed by a heartbeat every lease/3, reclaimed
  when expired. Delays reschedule the job (`available_at = resume_at`) instead of sleeping. Approval
  waits delete the job; approval re-enqueues it.
- **Alternatives:** Redis lists / streams (a second durable store whose state must agree with
  Postgres), RabbitMQ/Kafka (operational weight, and still needs an outbox for exactly-once), an
  in-process scheduler (loses work on `kill -9`).
- **Consequences:** Enqueue and run creation are one transaction, so no lost or phantom jobs.
  Multi-worker is safe by construction (SKIP LOCKED + the run row lock), though the demo runs one
  worker. Polling costs a cheap indexed query every 250 ms; `LISTEN/NOTIFY` could wake workers
  sooner if latency mattered.
