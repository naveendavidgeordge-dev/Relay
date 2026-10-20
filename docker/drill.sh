#!/usr/bin/env bash
# Kill-and-resume drill — EVALUATION_GUIDE.md §2, step for step:
#   1. POST /admin/reset on the mock world (clean slate)
#   2. trigger wf_slow_fulfillment with pay_201's input
#   3. wait until the confirmation email is in the ledger, then KILL -9 the worker during pack_delay
#   4. restart the worker; the run must resume and finish
#   5. python scripts/duplication_check.py --url <world>
#
# Usage: bash docker/drill.sh                      (docker-compose stack, container relay-worker)
#        WORKER_PID=12345 bash docker/drill.sh     (locally started worker JVM; you restart it)
set -euo pipefail

API_URL="${API_URL:-http://localhost:8080}"
WORLD_URL="${WORLD_URL:-http://localhost:9210}"
TOKEN="${TOKEN:-relay-demo-token}"
PY="${PY:-python3}"
COMPOSE="${COMPOSE:-docker compose -f docker/docker-compose.yml --env-file .env}"
WORKER_CONTAINER="${WORKER_CONTAINER:-relay-worker}"

json() { "$PY" -c "import json,sys; d=json.load(sys.stdin); print($1)"; }
say() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }

say "1/5 reset the mock world ledger"
curl -fsS -X POST "$WORLD_URL/admin/reset" >/dev/null

say "2/5 trigger wf_slow_fulfillment (payload pay_201)"
RUN_ID=$(curl -fsS -X POST "$API_URL/workflows/wf_slow_fulfillment/trigger" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"input": {"order_id": "ord_2003", "customer_email": "lena@example.com"}}' | json 'd["run_id"]')
echo "run_id=$RUN_ID"

say "3/5 wait for the confirmation email in the ledger, then kill -9 the worker during pack_delay"
for _ in $(seq 1 60); do
  N=$(curl -fsS "$WORLD_URL/admin/ledger" | json 'sum(1 for e in d["entries"] if e["action"]=="email.send")')
  [ "$N" -ge 1 ] && break
  sleep 0.5
done
[ "$N" -ge 1 ] || { echo "confirmation email never arrived"; exit 1; }
sleep 3   # well inside the 20 s delay
CURRENT=$(curl -fsS "$API_URL/runs/$RUN_ID" -H "Authorization: Bearer $TOKEN" | json 'd.get("current_node_id")')
echo "run is at node: $CURRENT (expected pack_delay)"
if [ -n "${WORKER_PID:-}" ]; then
  kill -9 "$WORKER_PID"
  echo "killed worker pid $WORKER_PID — restart it now (make worker); waiting 15 s"
  sleep 15
else
  docker kill --signal KILL "$WORKER_CONTAINER" >/dev/null
  echo "SIGKILL sent to $WORKER_CONTAINER at $(date -u +%H:%M:%S)Z (during pack_delay)"
  sleep 2
  say "4/5 restart the worker"
  $COMPOSE start worker >/dev/null
fi

say "4/5 wait for the run to finish"
STATUS=""
for _ in $(seq 1 90); do
  STATUS=$(curl -fsS "$API_URL/runs/$RUN_ID" -H "Authorization: Bearer $TOKEN" | json 'd["status"]')
  case "$STATUS" in succeeded|failed|cancelled) break ;; esac
  sleep 1
done
echo "final status: $STATUS"
curl -fsS "$API_URL/runs/$RUN_ID" -H "Authorization: Bearer $TOKEN" \
  | json 'chr(10).join("  #%s %-16s %-10s attempt=%s key=%s" % (s["sequence"], s["node_id"], s["status"], s["attempt"], s.get("idempotency_key")) for s in d["steps"])'
[ "$STATUS" = "succeeded" ] || { echo "run did not succeed"; exit 1; }

say "5/5 duplication check"
"$PY" scripts/duplication_check.py --url "$WORLD_URL"
