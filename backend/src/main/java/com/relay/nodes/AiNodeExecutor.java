package com.relay.nodes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.relay.config.RelayProperties;
import com.relay.domain.error.ErrorCode;
import com.relay.integrations.ai.AiModelPort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@code ai}: "Calls an LLM with a prompt template and validates the response against
 * output_schema (JSON Schema). On validation failure, retry once with the validation error
 * appended; if still invalid, fail the step. Token usage must be recorded in the step trace." —
 * node_catalog.json
 *
 * <p>Params (verbatim): {@code prompt} (required, templatable), {@code output_schema} (required,
 * object). Output: the validated JSON object ("defined by output_schema").
 *
 * <p>Validation happens <em>here</em>, in the engine, against the schema from the run's snapshot —
 * the sidecar's own validation is advisory and never trusted. A schema failure after the single
 * repair attempt throws {@link NodeFailureException} ({@code ai_schema_violation}), which the
 * engine does not retry. Both attempts appear in the step's attempt log with their errors and
 * tokens.
 */
@Component
public class AiNodeExecutor implements NodeExecutor {

  private static final Logger log = LoggerFactory.getLogger(AiNodeExecutor.class);
  private static final JsonSchemaFactory SCHEMAS =
      JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

  private final AiModelPort model;
  private final boolean logPrompts;
  private final ObjectMapper mapper = new ObjectMapper();

  /**
   * Creates the executor.
   *
   * @param model model port
   * @param props relay properties ({@code AI_LOG_PROMPTS})
   */
  public AiNodeExecutor(AiModelPort model, RelayProperties props) {
    this.model = model;
    this.logPrompts = props.ai() != null && props.ai().logPrompts();
  }

  @Override
  public String type() {
    return "ai";
  }

  @Override
  public NodeResult execute(NodeExecutionContext ctx) {
    JsonNode schemaNode = ctx.param("output_schema");
    if (!schemaNode.isObject()) {
      throw new NodeFailureException(ErrorCode.INVALID_PARAM_VALUE, "output_schema must be an object");
    }
    JsonSchema schema;
    try {
      schema = SCHEMAS.getSchema(schemaNode);
    } catch (RuntimeException e) {
      throw new NodeFailureException(
          ErrorCode.INVALID_PARAM_VALUE, "output_schema is not a valid JSON Schema");
    }
    String prompt = ctx.text("prompt", "");
    if (logPrompts) {
      log.info("AI prompt for {}: {}", ctx.nodeId(), prompt);
    }

    List<ObjectNode> details = new ArrayList<>();
    AiModelPort.ModelReply first = ask(ctx, prompt, schemaNode, null);
    Check c1 = check(first, schema);
    details.add(log(1, "initial", first, c1));
    TokenUsage tokens = new TokenUsage(first.tokensPrompt(), first.tokensCompletion());
    if (c1.valid()) {
      return new NodeResult.Completed(c1.output(), tokens, details);
    }

    // Exactly one repair attempt, with the validation errors appended.
    String feedback =
        "Your previous answer did not satisfy the required JSON Schema.\nValidation errors:\n- "
            + String.join("\n- ", c1.errors())
            + "\nPrevious answer: "
            + clip(first.rawText() != null ? first.rawText() : String.valueOf(first.output()), 500)
            + "\nRespond again with ONLY a JSON object that satisfies the schema.";
    AiModelPort.ModelReply second = ask(ctx, prompt, schemaNode, feedback);
    Check c2 = check(second, schema);
    details.add(log(2, "repair", second, c2));
    tokens = tokens.plus(new TokenUsage(second.tokensPrompt(), second.tokensCompletion()));
    if (c2.valid()) {
      return new NodeResult.Completed(c2.output(), tokens, details);
    }

    Map<String, Object> err = new LinkedHashMap<>();
    err.put("first_attempt_errors", c1.errors());
    err.put("repair_attempt_errors", c2.errors());
    err.put("tokens_prompt", tokens.prompt());
    err.put("tokens_completion", tokens.completion());
    err.put("ai_attempts", details.stream().map(d -> (Object) d).toList());
    throw new NodeFailureException(
        ErrorCode.AI_SCHEMA_VIOLATION,
        "model output failed output_schema validation after one repair retry: "
            + String.join("; ", c2.errors()),
        err);
  }

  private AiModelPort.ModelReply ask(
      NodeExecutionContext ctx, String prompt, JsonNode schema, String feedback) {
    return model.complete(
        new AiModelPort.ModelRequest(
            prompt, schema, feedback, ctx.runId(), ctx.nodeId(), ctx.traceId()));
  }

  private Check check(AiModelPort.ModelReply reply, JsonSchema schema) {
    JsonNode output = reply.output() != null ? reply.output() : lenientParse(reply.rawText());
    if (output == null) {
      return new Check(false, null, List.of("$: answer is not valid JSON"));
    }
    Set<ValidationMessage> errors = schema.validate(output);
    if (errors.isEmpty()) {
      return new Check(true, output, List.of());
    }
    return new Check(false, output, errors.stream().map(ValidationMessage::getMessage).sorted().toList());
  }

  /** Accepts a bare JSON object, or one wrapped in a Markdown code fence. */
  private JsonNode lenientParse(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String s = raw.strip();
    if (s.startsWith("```")) {
      int nl = s.indexOf('\n');
      int end = s.lastIndexOf("```");
      if (nl > 0 && end > nl) {
        s = s.substring(nl + 1, end).strip();
      }
    }
    try {
      return mapper.readTree(s);
    } catch (JsonProcessingException e) {
      return null;
    }
  }

  private static ObjectNode log(int attempt, String kind, AiModelPort.ModelReply r, Check c) {
    ObjectNode o = JsonNodeFactory.instance.objectNode();
    o.put("ai_attempt", attempt);
    o.put("kind", kind);
    o.put("valid", c.valid());
    o.put("provider", r.provider());
    if (r.model() != null) {
      o.put("model", r.model());
    }
    o.put("tokens_prompt", r.tokensPrompt());
    o.put("tokens_completion", r.tokensCompletion());
    if (!c.valid()) {
      ArrayNode errs = o.putArray("validation_errors");
      c.errors().forEach(errs::add);
      o.put("rejected_output", clip(r.rawText() != null ? r.rawText() : String.valueOf(r.output()), 300));
    }
    return o;
  }

  private static String clip(String s, int n) {
    return s == null ? null : s.length() <= n ? s : s.substring(0, n) + "…";
  }

  private record Check(boolean valid, JsonNode output, List<String> errors) {}
}
