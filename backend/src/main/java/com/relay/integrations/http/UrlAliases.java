package com.relay.integrations.http;

import java.util.ArrayList;
import java.util.List;

/**
 * Prefix rewrites applied to outbound URLs at dispatch time ({@code RELAY_URL_ALIASES}, e.g.
 * {@code http://localhost:9210=>http://mock-world:9210}).
 *
 * <p>Exists because two seed workflows hardcode {@code http://localhost:9210}, which inside
 * docker-compose is the backend container itself, not the mock world (DESIGN/00 conflict C2). The
 * stored definition, run snapshot and {@code resolved_input.url} keep the original URL; only the
 * request target changes, and the trace records {@code effective_url}.
 */
public final class UrlAliases {

  private final List<String[]> rules;

  private UrlAliases(List<String[]> rules) {
    this.rules = List.copyOf(rules);
  }

  /**
   * Parses {@code from=>to[,from=>to…]}. Blank input means no aliases.
   *
   * @param spec configuration string
   * @return aliases
   */
  public static UrlAliases parse(String spec) {
    List<String[]> rules = new ArrayList<>();
    if (spec != null) {
      for (String part : spec.split(",")) {
        String p = part.strip();
        if (p.isEmpty()) {
          continue;
        }
        int arrow = p.indexOf("=>");
        if (arrow <= 0 || arrow + 2 >= p.length()) {
          throw new IllegalArgumentException("RELAY_URL_ALIASES entry must be 'from=>to': " + p);
        }
        rules.add(new String[] {p.substring(0, arrow).strip(), p.substring(arrow + 2).strip()});
      }
    }
    return new UrlAliases(rules);
  }

  /**
   * Rewrites the first matching prefix.
   *
   * @param url original URL
   * @return the effective URL (unchanged when nothing matches)
   */
  public String apply(String url) {
    for (String[] r : rules) {
      if (url.startsWith(r[0])) {
        return r[1] + url.substring(r[0].length());
      }
    }
    return url;
  }
}
