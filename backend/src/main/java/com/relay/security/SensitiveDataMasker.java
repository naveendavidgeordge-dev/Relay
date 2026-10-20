package com.relay.security;

import com.fasterxml.jackson.core.JsonStreamContext;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import net.logstash.logback.mask.ValueMasker;

/**
 * Redacts secrets from every log value before it is written ("Never log secrets" — brief §3.4;
 * "webhook secrets and any model API keys never appear in traces, console views, or error
 * messages" — EVALUATION_GUIDE).
 *
 * <p>Two layers: (1) any JSON field whose name looks sensitive is replaced wholesale; (2) every
 * string value is scanned for token-shaped substrings (bearer tokens, {@code whsec_…}, API keys,
 * PEM blocks, JWTs, {@code X-Relay-Secret} headers).
 */
public class SensitiveDataMasker implements ValueMasker {

  /** Replacement text. */
  public static final String MASK = "****";

  private static final Set<String> SENSITIVE_FIELDS =
      Set.of(
          "secret",
          "password",
          "token",
          "authorization",
          "api_key",
          "apikey",
          "x-relay-secret",
          "private_key",
          "master_key",
          "column_key",
          "credential");

  private static final List<Pattern> PATTERNS =
      List.of(
          Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._\\-~+/=]{6,}"),
          Pattern.compile("(?i)(x-relay-secret[\"']?\\s*[:=]\\s*[\"']?)[^\\s\"',}]+"),
          Pattern.compile("()whsec_[A-Za-z0-9_\\-]+"),
          Pattern.compile("()rly_[A-Za-z0-9_\\-]{16,}"),
          Pattern.compile("()(?:sk-[A-Za-z0-9_\\-]{16,}|gsk_[A-Za-z0-9]{16,}|AIza[0-9A-Za-z_\\-]{30,})"),
          Pattern.compile("()eyJ[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+"),
          Pattern.compile(
              "()-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----"),
          Pattern.compile("(?i)((?:secret|password|api_key|token)[\"']?\\s*[:=]\\s*[\"']?)[^\\s\"',}]+"));

  @Override
  public Object mask(JsonStreamContext context, Object value) {
    if (context != null && context.hasCurrentName() && isSensitiveField(context.getCurrentName())) {
      return MASK;
    }
    if (value instanceof CharSequence cs) {
      String masked = mask(cs.toString());
      return masked.equals(cs.toString()) ? null : masked;
    }
    return null;
  }

  /**
   * Masks token-shaped substrings in free text.
   *
   * @param text any text
   * @return text with secrets replaced by {@link #MASK}
   */
  public static String mask(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    String out = text;
    for (Pattern p : PATTERNS) {
      out = p.matcher(out).replaceAll("$1" + MASK);
    }
    return out;
  }

  private static boolean isSensitiveField(String name) {
    String n = name.toLowerCase(Locale.ROOT);
    return SENSITIVE_FIELDS.contains(n) || n.endsWith("_secret") || n.endsWith("_token");
  }
}
