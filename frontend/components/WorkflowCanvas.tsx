import dagre from "dagre";
import type { Definition } from "@/lib/api/types";
import { cn } from "@/lib/utils";
import { NODE_META } from "./NodeTypeBadge";

const W = 188;
const H = 52;

export interface CanvasEdge {
  from: string;
  to: string;
  kind: "next" | "true" | "false";
  back: boolean;
}

export interface CanvasLayout {
  width: number;
  height: number;
  nodes: { id: string; type: string; x: number; y: number }[];
  edges: (CanvasEdge & { points: { x: number; y: number }[] })[];
}

/** Lays the definition out top-to-bottom with dagre. Pure function (unit tested). */
export function layoutDefinition(def: Definition): CanvasLayout {
  const g = new dagre.graphlib.Graph({ multigraph: true });
  g.setGraph({ rankdir: "TB", nodesep: 36, ranksep: 56, marginx: 16, marginy: 16 });
  g.setDefaultEdgeLabel(() => ({}));
  const ids = new Set(def.nodes.map((n) => n.id));
  def.nodes.forEach((n) => g.setNode(n.id, { width: W, height: H }));

  // Backward edges (to an earlier node in BFS order from entry) are drawn dashed.
  const order = new Map<string, number>();
  const queue = [def.entry];
  while (queue.length) {
    const id = queue.shift()!;
    if (order.has(id) || !ids.has(id)) continue;
    order.set(id, order.size);
    const n = def.nodes.find((x) => x.id === id);
    [n?.next, n?.on_true, n?.on_false].forEach((t) => t && queue.push(t));
  }

  const edges: CanvasEdge[] = [];
  for (const n of def.nodes) {
    const add = (to: string | null | undefined, kind: CanvasEdge["kind"]) => {
      if (!to || !ids.has(to)) return;
      const back = (order.get(to) ?? 0) <= (order.get(n.id) ?? 0);
      edges.push({ from: n.id, to, kind, back });
    };
    add(n.next, "next");
    add(n.on_true, "true");
    add(n.on_false, "false");
  }
  edges.forEach((e, i) => {
    // reverse back edges for layout so dagre keeps the forward flow top-to-bottom
    if (e.back) g.setEdge(e.to, e.from, {}, `e${i}`);
    else g.setEdge(e.from, e.to, {}, `e${i}`);
  });
  dagre.layout(g);

  const nodes = def.nodes.map((n) => {
    const p = g.node(n.id);
    return { id: n.id, type: n.type, x: p.x, y: p.y };
  });
  const laidEdges = edges.map((e, i) => {
    const pts = g.edge(e.back ? e.to : e.from, e.back ? e.from : e.to, `e${i}`)?.points ?? [];
    return { ...e, points: e.back ? [...pts].reverse() : pts };
  });
  const graph = g.graph();
  return { width: graph.width ?? 400, height: graph.height ?? 200, nodes, edges: laidEdges };
}

/** Read-only DAG of a definition. Condition branches are split visibly (true/false). */
export function WorkflowCanvas({
  definition,
  visited,
  current,
}: {
  definition: Definition;
  visited?: Set<string>;
  current?: string | null;
}) {
  const L = layoutDefinition(definition);
  return (
    <figure
      className="overflow-auto rounded-md border border-border bg-bg p-2"
      aria-label="Workflow graph"
    >
      <svg
        role="img"
        aria-label={`Graph of ${definition.name}`}
        width={L.width}
        height={L.height}
        viewBox={`0 0 ${L.width} ${L.height}`}
        className="mx-auto"
      >
        <defs>
          {(["next", "true", "false"] as const).map((k) => (
            <marker
              key={k}
              id={`arrow-${k}`}
              viewBox="0 0 10 10"
              refX="9"
              refY="5"
              markerWidth="6"
              markerHeight="6"
              orient="auto-start-reverse"
            >
              <path d="M0,0 L10,5 L0,10 z" className={edgeColor(k, "fill")} />
            </marker>
          ))}
        </defs>
        {L.edges.map((e, i) => {
          const d = e.points.map((p, j) => `${j === 0 ? "M" : "L"}${p.x},${p.y}`).join(" ");
          const mid = e.points[Math.floor(e.points.length / 2)];
          return (
            <g key={i} data-edge={`${e.from}->${e.to}`} data-kind={e.kind}>
              <path
                d={d}
                fill="none"
                strokeWidth={1.5}
                strokeDasharray={e.back ? "5 4" : undefined}
                className={edgeColor(e.kind, "stroke")}
                markerEnd={`url(#arrow-${e.kind})`}
              />
              {e.kind !== "next" && mid ? (
                <text
                  x={mid.x + 6}
                  y={mid.y}
                  className={cn("text-[10px] font-medium", edgeColor(e.kind, "fill"))}
                >
                  {e.kind}
                </text>
              ) : null}
            </g>
          );
        })}
        {L.nodes.map((n) => {
          const meta = NODE_META[n.type];
          const isEntry = n.id === definition.entry;
          const seen = visited?.has(n.id);
          return (
            <g key={n.id} transform={`translate(${n.x - W / 2},${n.y - H / 2})`} data-node={n.id}>
              <rect
                width={W}
                height={H}
                rx={8}
                className={cn(
                  "fill-surface stroke-border",
                  isEntry && "stroke-accent",
                  seen && "fill-accent/10",
                  current === n.id && "stroke-warn",
                )}
                strokeWidth={current === n.id || isEntry ? 2 : 1}
              />
              <text x={12} y={21} className="fill-fg font-mono text-[12px] font-medium">
                {n.id.length > 22 ? `${n.id.slice(0, 21)}…` : n.id}
              </text>
              <text
                x={12}
                y={38}
                className={cn(
                  "text-[10px]",
                  n.type === "order_action" ? "fill-bad" : "fill-subtle",
                )}
              >
                {meta?.label ?? n.type}
                {n.type === "order_action" ? " · requires approval" : ""}
                {isEntry ? " · entry" : ""}
              </text>
            </g>
          );
        })}
      </svg>
    </figure>
  );
}

function edgeColor(kind: CanvasEdge["kind"], attr: "fill" | "stroke"): string {
  if (kind === "true") return attr === "fill" ? "fill-ok" : "stroke-ok";
  if (kind === "false") return attr === "fill" ? "fill-bad" : "stroke-bad";
  return attr === "fill" ? "fill-subtle" : "stroke-subtle";
}
