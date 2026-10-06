package com.relay.domain.validation.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.relay.domain.error.ErrorCode;
import com.relay.domain.validation.ValidationContext;
import com.relay.domain.validation.ValidationRule;
import com.relay.domain.validation.Violation;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Graph-level checks from {@code entry}:
 *
 * <ul>
 *   <li>{@code cyclic_entry} — no node reachable from {@code entry} has a {@code null} edge, i.e.
 *       every path loops forever and no run could ever succeed. Ordinary loops with an exit (like
 *       {@code wf_runaway}, which can reach {@code done}) are never flagged: "Backward jumps (loops)
 *       are legal and must not be rejected" (API_CONTRACT §1). Severity is configurable; default
 *       warning (DESIGN/00 conflict C3).
 *   <li>{@code unreachable_node} (warning) — a node no path from {@code entry} reaches.
 * </ul>
 */
public final class ReachabilityRule extends ValidationRule {

  private final boolean rejectCyclicEntry;

  /**
   * Creates the rule.
   *
   * @param rejectCyclicEntry true to make {@code cyclic_entry} a blocking error
   */
  public ReachabilityRule(boolean rejectCyclicEntry) {
    this.rejectCyclicEntry = rejectCyclicEntry;
  }

  @Override
  protected boolean check(ValidationContext ctx) {
    Map<String, JsonNode> byId = ctx.nodesById();
    String entry = ctx.definition().path("entry").asText(null);
    if (entry == null || !byId.containsKey(entry)) {
      return true; // EntryRule reported it
    }
    Set<String> reachable = new LinkedHashSet<>();
    Deque<String> queue = new ArrayDeque<>();
    queue.add(entry);
    boolean canTerminate = false;
    while (!queue.isEmpty()) {
      String id = queue.poll();
      if (!reachable.add(id)) {
        continue;
      }
      JsonNode n = byId.get(id);
      boolean branching =
          ctx.catalog().node(n.path("type").asText(null)).map(s -> s.isBranching()).orElse(false);
      String[] edges = branching ? new String[] {"on_true", "on_false"} : new String[] {"next"};
      for (String edge : edges) {
        JsonNode target = n.get(edge);
        if (target == null || target.isNull()) {
          canTerminate = true;
        } else if (target.isTextual() && byId.containsKey(target.asText())) {
          queue.add(target.asText());
        }
      }
    }
    boolean danglingEdges =
        ctx.violations().stream().anyMatch(v -> v.code() == ErrorCode.INVALID_EDGE_TARGET);
    if (!canTerminate && !danglingEdges) {
      String msg =
          "No path from entry '" + entry + "' ever ends the run: every route loops forever"
              + " (the run can only stop at the max_steps cap)";
      ctx.add(
          rejectCyclicEntry
              ? Violation.error(ErrorCode.CYCLIC_ENTRY, msg, entry, "entry")
              : Violation.warning(ErrorCode.CYCLIC_ENTRY, msg, entry, "entry"));
    }
    for (String id : byId.keySet()) {
      if (!reachable.contains(id)) {
        ctx.add(
            Violation.warning(
                ErrorCode.UNREACHABLE_NODE,
                "Node '" + id + "' is not reachable from entry '" + entry + "'",
                id,
                null));
      }
    }
    return true;
  }
}
