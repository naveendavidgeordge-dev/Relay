package com.relay.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relay.config.RelayProperties;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The annotated trigger bodies from {@code data/sample_payloads.jsonl} (inputs only, not stored
 * entities — DATA_MODEL "Source Data Files"). Exposed read-only so the console can offer "trigger
 * with sample" and the demo can reference ids such as {@code pay_inject_001}.
 */
@Component
public class SamplePayloads {

  private final List<JsonNode> samples;

  /**
   * Loads every non-blank JSONL line.
   *
   * @param props relay properties
   * @param mapper Jackson mapper
   */
  public SamplePayloads(RelayProperties props, ObjectMapper mapper) {
    List<JsonNode> loaded = new ArrayList<>();
    try (BufferedReader r =
        new BufferedReader(
            new InputStreamReader(
                props.seed().samples().getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = r.readLine()) != null) {
        if (!line.isBlank()) {
          loaded.add(mapper.readTree(line));
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read sample payloads", e);
    }
    this.samples = List.copyOf(loaded);
  }

  /** All samples in file order. */
  public List<JsonNode> all() {
    return samples.stream().map(s -> (JsonNode) s.deepCopy()).toList();
  }

  /** A sample by id, e.g. {@code pay_201}. */
  public Optional<JsonNode> byId(String id) {
    return samples.stream()
        .filter(s -> id.equals(s.path("id").asText()))
        .findFirst()
        .map(s -> (JsonNode) s.deepCopy());
  }
}
