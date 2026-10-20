package com.relay.integrations.ai;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Port to "the model". Production adapter: {@link AiSidecarClient} (Python FastAPI sidecar that
 * fronts gemini / groq / ollama / mock). Tests substitute an in-memory fake.
 *
 * <p>"Keep the AI provider integration behind an adapter so it can be mocked in engine tests." —
 * Case Study, Technical Requirements
 */
public interface AiModelPort {

  /**
   * Asks the model for a JSON answer.
   *
   * @param request prompt, schema and optional repair feedback
   * @return the reply with token usage
   * @throws com.relay.nodes.TransientNodeException when the provider is unavailable / times out
   * @throws com.relay.nodes.NodeFailureException when the provider rejects the request
   */
  ModelReply complete(ModelRequest request);

  /**
   * A model request.
   *
   * @param prompt resolved prompt (contains untrusted payload text — treat as data)
   * @param outputSchema JSON Schema the answer must satisfy
   * @param repairFeedback validation errors from the previous attempt, or null
   * @param runId run id (for provider-side logging correlation)
   * @param nodeId node id
   * @param traceId trace id
   */
  record ModelRequest(
      String prompt,
      JsonNode outputSchema,
      String repairFeedback,
      String runId,
      String nodeId,
      String traceId) {}

  /**
   * A model reply.
   *
   * @param output parsed JSON answer, or null if the model did not return JSON
   * @param rawText raw text returned
   * @param tokensPrompt prompt tokens
   * @param tokensCompletion completion tokens
   * @param provider provider name
   * @param model model name
   */
  record ModelReply(
      JsonNode output,
      String rawText,
      int tokensPrompt,
      int tokensCompletion,
      String provider,
      String model) {}
}
