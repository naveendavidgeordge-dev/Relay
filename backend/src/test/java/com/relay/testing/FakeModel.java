package com.relay.testing;

import com.fasterxml.jackson.databind.JsonNode;
import com.relay.integrations.ai.AiModelPort;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * In-memory {@link AiModelPort} for engine tests: replies are scripted in order; every request is
 * recorded so tests can assert on the repair feedback.
 */
public final class FakeModel implements AiModelPort {

  private final Deque<String> replies = new ArrayDeque<>();
  public final List<ModelRequest> requests = new ArrayList<>();

  /** Queues raw-text replies (JSON or garbage). */
  public FakeModel reply(String... raw) {
    replies.addAll(List.of(raw));
    return this;
  }

  /** Clears queued replies and recorded requests. */
  public synchronized void reset() {
    replies.clear();
    requests.clear();
  }

  @Override
  public synchronized ModelReply complete(ModelRequest request) {
    requests.add(request);
    String raw = replies.isEmpty() ? "{}" : replies.poll();
    JsonNode parsed;
    try {
      parsed = Fixtures.MAPPER.readTree(raw);
      if (!parsed.isObject()) {
        parsed = null;
      }
    } catch (Exception e) {
      parsed = null;
    }
    return new ModelReply(parsed, raw, 40, 12, "fake", "fake-1");
  }
}
