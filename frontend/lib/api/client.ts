import { z } from "zod";
import {
  ApprovalSchema,
  CompileResultSchema,
  ErrorBodySchema,
  RunSchema,
  SampleSchema,
  TriggerResultSchema,
  ValidationSchema,
  WorkflowSchema,
  type RunStatus,
} from "./types";

/** An API error with the backend's uniform `{error: {message, code, trace_id}}` body. */
export class RelayApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: string,
    readonly traceId?: string | null,
    readonly details?: unknown,
  ) {
    super(message);
    this.name = "RelayApiError";
  }
}

export type FetchLike = (input: string, init?: RequestInit) => Promise<Response>;

export interface ClientOptions {
  baseUrl: string;
  token?: string;
  fetch?: FetchLike;
  /** Extra fetch options (e.g. Next.js `cache: "no-store"`). */
  init?: RequestInit;
}

export interface RunFilters {
  status?: RunStatus | "";
  workflowId?: string;
  from?: string;
  to?: string;
  limit?: number;
}

/**
 * Typed Relay client. Every response is validated with Zod so a contract drift fails loudly at the
 * edge instead of rendering `undefined` deep in a component.
 */
export function createRelayClient(opts: ClientOptions) {
  const doFetch: FetchLike = opts.fetch ?? ((i, init) => fetch(i, init));
  const base = opts.baseUrl.replace(/\/$/, "");

  async function request<T>(
    schema: z.ZodType<T>,
    method: string,
    path: string,
    body?: unknown,
  ): Promise<T> {
    const headers: Record<string, string> = { Accept: "application/json" };
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (opts.token) headers.Authorization = `Bearer ${opts.token}`;
    const res = await doFetch(`${base}${path}`, {
      ...opts.init,
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await res.text();
    const data: unknown = text ? safeJson(text) : null;
    if (!res.ok) {
      const parsed = ErrorBodySchema.safeParse(data);
      if (parsed.success) {
        const e = parsed.data.error;
        throw new RelayApiError(e.message, res.status, e.code, e.trace_id, e.details);
      }
      throw new RelayApiError(`Request failed with ${res.status}`, res.status, "http_error");
    }
    const result = schema.safeParse(data);
    if (!result.success) {
      throw new RelayApiError(
        `Unexpected response shape from ${method} ${path}: ${result.error.issues[0]?.message ?? "invalid"}`,
        res.status,
        "contract_mismatch",
      );
    }
    return result.data;
  }

  const qs = (params: Record<string, string | number | undefined>) => {
    const s = new URLSearchParams();
    for (const [k, v] of Object.entries(params))
      if (v !== undefined && v !== "") s.set(k, String(v));
    const out = s.toString();
    return out ? `?${out}` : "";
  };

  return {
    listWorkflows: (status?: "draft" | "published") =>
      request(z.array(WorkflowSchema), "GET", `/workflows${qs({ status })}`),
    getWorkflow: (id: string) =>
      request(WorkflowSchema, "GET", `/workflows/${encodeURIComponent(id)}`),
    createWorkflow: (definition: unknown) =>
      request(WorkflowSchema, "POST", "/workflows", definition),
    updateWorkflow: (id: string, definition: unknown) =>
      request(WorkflowSchema, "PUT", `/workflows/${encodeURIComponent(id)}`, definition),
    publishWorkflow: (id: string) =>
      request(WorkflowSchema, "POST", `/workflows/${encodeURIComponent(id)}/publish`),
    validateWorkflow: (definition: unknown) =>
      request(ValidationSchema, "POST", "/workflows/validate", definition),
    triggerWorkflow: (id: string, input: Record<string, unknown>) =>
      request(TriggerResultSchema, "POST", `/workflows/${encodeURIComponent(id)}/trigger`, {
        input,
      }),
    compile: (description: string) =>
      request(CompileResultSchema, "POST", "/workflows/compile", { description }),

    listRuns: (f: RunFilters = {}) =>
      request(
        z.array(RunSchema),
        "GET",
        `/runs${qs({ status: f.status, workflow_id: f.workflowId, from: f.from, to: f.to, limit: f.limit })}`,
      ),
    getRun: (id: string) => request(RunSchema, "GET", `/runs/${encodeURIComponent(id)}`),
    cancelRun: (id: string) => request(RunSchema, "POST", `/runs/${encodeURIComponent(id)}/cancel`),

    listApprovals: (status?: string) =>
      request(z.array(ApprovalSchema), "GET", `/approvals${qs({ status })}`),
    approve: (id: string, comment?: string) =>
      request(ApprovalSchema, "POST", `/approvals/${encodeURIComponent(id)}/approve`, { comment }),
    reject: (id: string, comment?: string) =>
      request(ApprovalSchema, "POST", `/approvals/${encodeURIComponent(id)}/reject`, { comment }),

    listSamples: (workflow?: string) =>
      request(z.array(SampleSchema), "GET", `/samples${qs({ workflow })}`),
  };
}

export type RelayClient = ReturnType<typeof createRelayClient>;

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return { _raw: text };
  }
}
