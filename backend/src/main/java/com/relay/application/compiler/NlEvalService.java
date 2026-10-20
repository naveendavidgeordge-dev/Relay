package com.relay.application.compiler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relay.domain.error.ErrorCode;
import com.relay.domain.validation.PublishValidator;
import com.relay.domain.validation.ValidationReport;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * One-command eval over {@code data/nl_eval.jsonl} (EVALUATION_GUIDE §4). Each case's {@code
 * description} — and nothing else — goes to the compiler; {@code assertions} are only used to
 * score. Nothing is persisted.
 *
 * <p>Scoring a {@code workflow} case — all must hold: compiled (not refused), no invented types,
 * passes publish validation, the asserted trigger ({@code schedule_or_loop} accepts either), every
 * {@code required_node_types} present, a condition with both branch edges declared when {@code
 * requires_branching}, and an approval on every path to an {@code order_action} when {@code
 * approval_must_precede_order_action}. A {@code refusal} case passes when the compiler refused
 * with an explanation and emitted no invented types.
 */
@Service
public class NlEvalService {

  private final CompilerService compiler;
  private final CompilerGuardrails guardrails;
  private final PublishValidator validator;
  private final ObjectMapper mapper;

  /**
   * Creates the eval.
   *
   * @param compiler compiler service
   * @param guardrails guardrails
   * @param validator publish validator
   * @param mapper Jackson mapper
   */
  public NlEvalService(
      CompilerService compiler,
      CompilerGuardrails guardrails,
      PublishValidator validator,
      ObjectMapper mapper) {
    this.compiler = compiler;
    this.guardrails = guardrails;
    this.validator = validator;
    this.mapper = mapper;
  }

  /** Runs every case and returns the report. */
  public Report run() {
    List<CaseResult> results = new ArrayList<>();
    String used = null;
    for (JsonNode c : cases()) {
      CompilerService.Result r = compiler.compile(c.path("description").asText(), false, "eval");
      used = r.compiler();
      results.add(score(c, r));
    }
    long correct = results.stream().filter(x -> "pass".equals(x.result())).count();
    double accuracy = results.isEmpty() ? 0 : Math.round(1000.0 * correct / results.size()) / 1000.0;
    return new Report(results.size(), (int) correct, accuracy, used, results);
  }

  private CaseResult score(JsonNode c, CompilerService.Result r) {
    String id = c.path("id").asText();
    String expected = c.path("expect").asText();
    JsonNode a = c.path("assertions");
    Map<String, Boolean> checks = new LinkedHashMap<>();
    JsonNode def = r.definition();
    boolean noInvented = def == null || guardrails.inventedTypes(def).isEmpty();
    checks.put("no_invented_types", noInvented);

    if ("refusal".equals(expected)) {
      boolean refused = r.refusal() != null && !r.refusal().reason().isBlank();
      checks.put("refused_with_explanation", refused);
      return new CaseResult(id, expected, pass(checks), checks, r.refusal() == null ? r.explanation() : r.refusal().reason(), types(def));
    }

    checks.put("compiled", def != null);
    if (def == null) {
      return new CaseResult(id, expected, "fail", checks, r.refusal() == null ? null : r.refusal().reason(), List.of());
    }
    ValidationReport report = validator.validate(def);
    checks.put("publishes", report.isValid());
    String trigger = def.path("trigger").path("type").asText();
    String wanted = a.path("trigger").asText(null);
    if (wanted != null) {
      checks.put(
          "trigger",
          "schedule_or_loop".equals(wanted) ? "schedule".equals(trigger) || hasLoop(def) : wanted.equals(trigger));
    }
    Set<String> present = new HashSet<>(types(def));
    List<String> required = new ArrayList<>();
    a.path("required_node_types").forEach(t -> required.add(t.asText()));
    checks.put("required_node_types", present.containsAll(required));
    if (a.path("requires_branching").asBoolean(false)) {
      checks.put("branching", hasBranching(def));
    }
    if (a.path("approval_must_precede_order_action").asBoolean(false)) {
      checks.put(
          "approval_precedes_order_action",
          present.contains("order_action")
              && report.warnings().stream().noneMatch(v -> v.code() == ErrorCode.APPROVAL_NOT_GUARANTEED));
    }
    return new CaseResult(id, expected, pass(checks), checks, r.explanation(), types(def));
  }

  private static String pass(Map<String, Boolean> checks) {
    return checks.values().stream().allMatch(Boolean::booleanValue) ? "pass" : "fail";
  }

  private static List<String> types(JsonNode def) {
    List<String> out = new ArrayList<>();
    if (def != null) {
      def.path("nodes").forEach(n -> out.add(n.path("type").asText()));
    }
    return out;
  }

  /** A condition whose two branch edges are declared, at least one leading somewhere. */
  private static boolean hasBranching(JsonNode def) {
    for (JsonNode n : def.path("nodes")) {
      if ("condition".equals(n.path("type").asText())
          && n.has("on_true")
          && n.has("on_false")
          && (n.hasNonNull("on_true") || n.hasNonNull("on_false"))) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasLoop(JsonNode def) {
    Map<String, Integer> order = new LinkedHashMap<>();
    List<String> queue = new ArrayList<>(List.of(def.path("entry").asText()));
    while (!queue.isEmpty()) {
      String id = queue.remove(0);
      if (order.containsKey(id)) {
        continue;
      }
      order.put(id, order.size());
      for (JsonNode n : def.path("nodes")) {
        if (id.equals(n.path("id").asText())) {
          for (String e : new String[] {"next", "on_true", "on_false"}) {
            if (n.hasNonNull(e)) {
              String t = n.get(e).asText();
              if (order.containsKey(t)) {
                return true;
              }
              queue.add(t);
            }
          }
        }
      }
    }
    return false;
  }

  private List<JsonNode> cases() {
    List<JsonNode> out = new ArrayList<>();
    try (BufferedReader r =
        new BufferedReader(
            new InputStreamReader(
                new ClassPathResource("seed/nl_eval.jsonl").getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = r.readLine()) != null) {
        if (!line.isBlank()) {
          out.add(mapper.readTree(line));
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out;
  }

  /**
   * Eval report (EVALUATION_GUIDE §4 shape).
   *
   * @param total cases
   * @param correct passed cases
   * @param accuracy correct / total
   * @param compiler strategy used
   * @param cases per-case results
   */
  public record Report(int total, int correct, double accuracy, String compiler, List<CaseResult> cases) {}

  /**
   * One case.
   *
   * @param id case id
   * @param expected {@code workflow} / {@code refusal}
   * @param result {@code pass} / {@code fail}
   * @param checks individual checks
   * @param explanation compiler explanation or refusal reason
   * @param nodeTypes node types emitted
   */
  public record CaseResult(
      String id,
      String expected,
      String result,
      Map<String, Boolean> checks,
      String explanation,
      List<String> nodeTypes) {}
}
