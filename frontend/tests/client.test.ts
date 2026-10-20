import { createRelayClient, RelayApiError } from "@/lib/api/client";

function fakeFetch(status: number, body: unknown) {
  const calls: { url: string; init?: RequestInit }[] = [];
  const fn = async (url: string, init?: RequestInit) => {
    calls.push({ url, init });
    return new Response(JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json" },
    });
  };
  return { fn, calls };
}

const run = {
  run_id: "run_1",
  workflow_id: "wf_expense_approval",
  workflow_version: 1,
  status: "waiting_approval",
  trigger_type: "webhook",
  steps_executed: 2,
  ai_tokens_used: 0,
  cancel_requested: false,
  created_at: "2026-07-04T10:02:11Z",
  steps: [
    {
      sequence: 1,
      node_id: "is_large",
      type: "condition",
      status: "succeeded",
      attempt: 1,
      started_at: "2026-07-04T10:02:12Z",
      side_effect: false,
      requires_approval: false,
    },
  ],
};

describe("Relay API client", () => {
  it("sends the bearer token and parses a run trace", async () => {
    const { fn, calls } = fakeFetch(200, run);
    const api = createRelayClient({ baseUrl: "http://relay/", token: "t0k", fetch: fn });
    const r = await api.getRun("run_1");
    expect(r.steps?.[0]?.node_id).toBe("is_large");
    expect(calls[0]?.url).toBe("http://relay/runs/run_1");
    expect((calls[0]?.init?.headers as Record<string, string>).Authorization).toBe("Bearer t0k");
  });

  it("sends the trigger body in the contract shape {input: {...}}", async () => {
    const { fn, calls } = fakeFetch(202, {
      run_id: "run_2",
      status: "queued",
      workflow_id: "wf",
      workflow_version: 1,
    });
    const api = createRelayClient({ baseUrl: "http://relay", fetch: fn });
    const res = await api.triggerWorkflow("wf", { a: 1 });
    expect(res.run_id).toBe("run_2");
    expect(JSON.parse(String(calls[0]?.init?.body))).toEqual({ input: { a: 1 } });
  });

  it("turns the uniform error body into a RelayApiError", async () => {
    const { fn } = fakeFetch(409, {
      error: {
        message: "Approval 'apr_1' is already approved",
        code: "approval_already_decided",
        trace_id: "abc",
      },
    });
    const api = createRelayClient({ baseUrl: "http://relay", fetch: fn });
    await expect(api.approve("apr_1")).rejects.toMatchObject({
      name: "RelayApiError",
      status: 409,
      code: "approval_already_decided",
      traceId: "abc",
    });
  });

  it("rejects a response that drifts from the contract", async () => {
    const { fn } = fakeFetch(200, { ...run, status: "paused" });
    const api = createRelayClient({ baseUrl: "http://relay", fetch: fn });
    const err = await api.getRun("run_1").catch((e: unknown) => e);
    expect(err).toBeInstanceOf(RelayApiError);
    expect((err as RelayApiError).code).toBe("contract_mismatch");
  });

  it("encodes filters into the query string", async () => {
    const { fn, calls } = fakeFetch(200, []);
    const api = createRelayClient({ baseUrl: "http://relay", fetch: fn });
    await api.listRuns({ status: "failed", workflowId: "wf_runaway", limit: 5 });
    expect(calls[0]?.url).toBe("http://relay/runs?status=failed&workflow_id=wf_runaway&limit=5");
  });
});
