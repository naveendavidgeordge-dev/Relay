import {
  Bell,
  Box,
  GitBranch,
  Globe,
  ShieldCheck,
  ShoppingCart,
  Sparkles,
  Timer,
  UserCheck,
} from "lucide-react";
import { cn } from "@/lib/utils";

export const NODE_META: Record<string, { label: string; Icon: typeof Box; tone: string }> = {
  http_request: { label: "HTTP", Icon: Globe, tone: "text-info" },
  condition: { label: "Condition", Icon: GitBranch, tone: "text-muted" },
  delay: { label: "Delay", Icon: Timer, tone: "text-muted" },
  notify: { label: "Notify", Icon: Bell, tone: "text-info" },
  ai: { label: "AI", Icon: Sparkles, tone: "text-accent" },
  approval: { label: "Approval", Icon: UserCheck, tone: "text-warn" },
  order_action: { label: "Order action", Icon: ShoppingCart, tone: "text-bad" },
};

/** Icon + label per catalog node type, with a shield overlay when the type requires approval. */
export function NodeTypeBadge({
  type,
  requiresApproval,
  compact = false,
}: {
  type: string;
  requiresApproval?: boolean;
  compact?: boolean;
}) {
  const meta = NODE_META[type] ?? { label: type, Icon: Box, tone: "text-muted" };
  const gated = requiresApproval ?? type === "order_action";
  return (
    <span
      data-node-type={type}
      className="inline-flex items-center gap-1.5 rounded-md border border-border bg-elevated px-1.5 py-0.5 text-2xs font-medium text-fg"
      title={gated ? `${meta.label} — requires approval` : meta.label}
    >
      <span className="relative inline-flex">
        <meta.Icon aria-hidden className={cn("size-3.5", meta.tone)} />
        {gated ? (
          <ShieldCheck
            aria-label="requires approval"
            className="absolute -bottom-1 -right-1.5 size-2.5 rounded-full bg-surface text-warn"
          />
        ) : null}
      </span>
      {compact ? null : meta.label}
    </span>
  );
}
