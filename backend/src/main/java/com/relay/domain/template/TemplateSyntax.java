package com.relay.domain.template;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The template grammar shared by publish validation and the engine's resolver.
 *
 * <p>"Path lookups ({@code {{trigger.body.x.y}}}, {@code {{nodes.id.output.z}}}) interpolated into
 * strings. No expressions, no filters, no arithmetic." — IMPLEMENTATION_GUIDE, FAQ
 *
 * <pre>
 *   template   := "{{" ws path ws "}}"
 *   path       := "trigger.body" ("." segment)*
 *               | "nodes." nodeId ".output" ("." segment)*
 *   segment    := [A-Za-z0-9_-]+          (digits index into arrays)
 * </pre>
 */
public final class TemplateSyntax {

  /** Matches one {@code {{ … }}} occurrence; group 1 is the trimmed expression. */
  public static final Pattern TEMPLATE = Pattern.compile("\\{\\{\\s*([^{}]*?)\\s*}}");

  /** Sentinel expression returned by {@link #expressions(String)} for an unclosed double brace. */
  public static final String UNBALANCED = "\u0000unbalanced";

  private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9_\\-]+");

  private TemplateSyntax() {}

  /** True if {@code s} contains at least one template occurrence (well-formed or not). */
  public static boolean containsTemplate(String s) {
    return s != null && s.contains("{{");
  }

  /**
   * True if the whole string is exactly one template (whitespace allowed around it), in which case
   * the resolver preserves the referenced value's JSON type instead of stringifying it.
   *
   * @param s candidate string
   * @return true for a single, whole-string template
   */
  public static boolean isWholeTemplate(String s) {
    if (s == null) {
      return false;
    }
    Matcher m = TEMPLATE.matcher(s.strip());
    return m.matches();
  }

  /**
   * Extracts every template expression in {@code s}. Unbalanced braces are reported as an
   * unparseable expression so validation can flag them.
   *
   * @param s a string param value
   * @return expressions (trimmed) in order of appearance
   */
  public static List<String> expressions(String s) {
    List<String> out = new ArrayList<>();
    if (s == null) {
      return out;
    }
    Matcher m = TEMPLATE.matcher(s);
    int consumedOpen = 0;
    while (m.find()) {
      out.add(m.group(1));
      consumedOpen++;
    }
    int opens = countOccurrences(s, "{{");
    if (opens > consumedOpen) {
      out.add(UNBALANCED);
    }
    return out;
  }

  /**
   * Parses one expression.
   *
   * @param expression text between the braces, trimmed
   * @return the reference, or empty if it does not match the grammar
   */
  public static Optional<TemplateRef> parse(String expression) {
    if (expression == null || expression.isBlank()) {
      return Optional.empty();
    }
    String[] parts = expression.strip().split("\\.", -1);
    for (String p : parts) {
      if (!SEGMENT.matcher(p).matches()) {
        return Optional.empty();
      }
    }
    if (parts.length >= 2 && parts[0].equals("trigger") && parts[1].equals("body")) {
      return Optional.of(
          new TemplateRef(TemplateRef.Root.TRIGGER_BODY, null, tail(parts, 2), expression));
    }
    if (parts.length >= 3 && parts[0].equals("nodes") && parts[2].equals("output")) {
      return Optional.of(
          new TemplateRef(TemplateRef.Root.NODE_OUTPUT, parts[1], tail(parts, 3), expression));
    }
    return Optional.empty();
  }

  private static List<String> tail(String[] parts, int from) {
    return List.copyOf(Arrays.asList(parts).subList(from, parts.length));
  }

  private static int countOccurrences(String s, String needle) {
    int count = 0;
    int idx = s.indexOf(needle);
    while (idx >= 0) {
      count++;
      idx = s.indexOf(needle, idx + needle.length());
    }
    return count;
  }
}
