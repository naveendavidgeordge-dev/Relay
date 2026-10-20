import { z } from "zod";

/**
 * Runtime-validated shapes of the Relay API (snake_case on the wire, as the backend emits).
 * The backend omits null fields, so optional fields are `.nullish()`.
 */

export const RUN_STATUSES = [
  "queued",
  "running",
  "waiting_approval",
  "succeeded",
  "failed",
  "cancelled",
] as const;
export const RunStatusSchema = z.enum(RUN_STATUSES);
export type RunStatus = z.infer<typeof RunStatusSchema>;

export const TERMINAL: ReadonlySet<RunStatus> = new Set(["succeeded", "failed", "cancelled"]);

const json = z.unknown();

export const ViolationSchema = z.object({
  code: z.string(),
  message: z.string(),
  node_id: z.string().nullish(),
  field: z.string().nullish(),
});
export type Violation = z.infer<typeof ViolationSchema>;

export const ValidationSchema = z.object({
  valid: z.boolean(),
  errors: z.array(ViolationSchema),
  warnings: z.array(ViolationSchema),
});
export type Validation = z.infer<typeof ValidationSchema>;

export const NodeDefSchema = z
  .object({
    id: z.string(),
    type: z.string(),
    params: z.record(json).nullish(),
    next: z.string().nullish(),
    on_true: z.string().nullish(),
    on_false: z.string().nullish(),
  })
  .passthrough();
export type NodeDef = z.infer<typeof NodeDefSchema>;

export const DefinitionSchema = z
  .object({
    id: z.string(),
    name: z.string(),
    description: z.string().nullish(),
    trigger: z.object({ type: z.string() }).passthrough(),
    entry: z.string(),
    limits: z.record(json).nullish(),
    nodes: z.array(NodeDefSchema),
  })
  .passthrough();
export type Definition = z.infer<typeof DefinitionSchema>;

export const VersionSchema = z.object({
  version: z.number(),
  published_by: z.string(),
  created_at: z.string(),
  signature_algorithm: z.string().nullish(),
  signing_key_id: z.string().nullish(),
  signature_valid: z.boolean().nullish(),
});

export const WorkflowSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string().nullish(),
  status: z.enum(["draft", "published"]),
  trigger_type: z.string(),
  published_version: z.number().nullish(),
  has_unpublished_changes: z.boolean(),
  created_at: z.string(),
  updated_at: z.string(),
  run_count: z.number(),
  last_run_at: z.string().nullish(),
  last_run_status: RunStatusSchema.nullish(),
  webhook_path: z.string().nullish(),
  definition: DefinitionSchema.nullish(),
  published_definition: DefinitionSchema.nullish(),
  versions: z.array(VersionSchema).nullish(),
  validation: ValidationSchema.nullish(),
});
export type Workflow = z.infer<typeof WorkflowSchema>;

export const ApprovalSchema = z.object({
  id: z.string(),
  run_id: z.string(),
  workflow_id: z.string().nullish(),
  node_id: z.string(),
  step_sequence: z.number(),
  message: z.string(),
  status: z.enum(["pending", "approved", "rejected", "cancelled"]),
  created_at: z.string(),
  decided_by: z.string().nullish(),
  decided_at: z.string().nullish(),
  comment: z.string().nullish(),
  signature_valid: z.boolean().nullish(),
});
export type Approval = z.infer<typeof ApprovalSchema>;

export const StepSchema = z.object({
  sequence: z.number(),
  node_id: z.string(),
  type: z.string(),
  status: z.string(),
  attempt: z.number(),
  input: json.nullish(),
  output: json.nullish(),
  error: z
    .object({ code: z.string(), message: z.string(), details: json.nullish() })
    .passthrough()
    .nullish(),
  started_at: z.string(),
  finished_at: z.string().nullish(),
  duration_ms: z.number().nullish(),
  tokens_prompt: z.number().nullish(),
  tokens_completion: z.number().nullish(),
  idempotency_key: z.string().nullish(),
  approval_id: z.string().nullish(),
  resume_at: z.string().nullish(),
  side_effect: z.boolean(),
  requires_approval: z.boolean(),
  attempts: z.array(z.record(json)).nullish(),
});
export type Step = z.infer<typeof StepSchema>;

export const RunSchema = z.object({
  run_id: z.string(),
  workflow_id: z.string(),
  workflow_name: z.string().nullish(),
  workflow_version: z.number(),
  status: RunStatusSchema,
  trigger_type: z.string(),
  input: json.nullish(),
  current_node_id: z.string().nullish(),
  steps_executed: z.number(),
  max_steps: z.number().nullish(),
  ai_tokens_used: z.number(),
  error: z
    .object({ code: z.string(), message: z.string(), details: json.nullish() })
    .passthrough()
    .nullish(),
  failure_reason: z.string().nullish(),
  cancel_requested: z.boolean(),
  created_at: z.string(),
  started_at: z.string().nullish(),
  finished_at: z.string().nullish(),
  duration_ms: z.number().nullish(),
  trace_id: z.string().nullish(),
  lease_owner: z.string().nullish(),
  signature_valid: z.boolean().nullish(),
  signature_key_id: z.string().nullish(),
  definition_snapshot: DefinitionSchema.nullish(),
  approvals: z.array(ApprovalSchema).nullish(),
  steps: z.array(StepSchema).nullish(),
});
export type Run = z.infer<typeof RunSchema>;

export const TriggerResultSchema = z.object({
  run_id: z.string(),
  status: z.string(),
  workflow_id: z.string(),
  workflow_version: z.number(),
});
export type TriggerResult = z.infer<typeof TriggerResultSchema>;

export const SampleSchema = z
  .object({
    id: z.string(),
    workflow: z.string(),
    note: z.string().nullish(),
    body: z.record(json),
  })
  .passthrough();
export type Sample = z.infer<typeof SampleSchema>;

export const CompileResultSchema = z.object({
  workflow: DefinitionSchema.nullish(),
  validation: ValidationSchema.nullish(),
  explanation: z.string().nullish(),
  refusal: z
    .object({ reason: z.string(), missing_capabilities: z.array(z.string()).nullish() })
    .nullish(),
  notes: z.array(z.string()).nullish(),
  workflow_id: z.string().nullish(),
  compiler: z.string().nullish(),
});
export type CompileResult = z.infer<typeof CompileResultSchema>;

export const ErrorBodySchema = z.object({
  error: z.object({
    message: z.string(),
    code: z.string(),
    trace_id: z.string().nullish(),
    details: json.nullish(),
  }),
});
