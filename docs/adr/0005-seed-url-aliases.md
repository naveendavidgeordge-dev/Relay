# ADR-0005: Dispatch-time URL aliases for the hardcoded mock-world URL

- **Status:** accepted (owner approved at the step-6 checkpoint)
- **Context:** `wf_slow_fulfillment.create_shipment` and `wf_runaway.check_order` hardcode
  `http://localhost:9210`. Inside docker-compose, `localhost` in the worker container is the worker
  itself. Fixtures are read-only (brief §16 rule 7). The seed file's own comment says the mock-world
  URL "should be engine configuration".
- **Decision:** `RELAY_URL_ALIASES` (`from=>to` prefix rewrites) is applied by `OutboundHttp` at
  request time only. The stored definition, the signed snapshot and the step's `resolved_input.url`
  keep the original URL; the step's attempt log records `effective_url`. Compose sets
  `http://localhost:9210=>http://mock-world:9210`. Local (non-Docker) runs need no alias.
- **Alternatives:** Edit the seeds (forbidden); `network_mode: host` (not portable to Docker Desktop
  on macOS/Windows); a template variable like `{{env.MOCK_WORLD_URL}}` (needs fixture edits and
  widens the template grammar).
- **Consequences:** The trace stays faithful to what the author wrote and shows where the request
  actually went. Aliases are prefix-only and operator-controlled; they cannot be set by payloads.
