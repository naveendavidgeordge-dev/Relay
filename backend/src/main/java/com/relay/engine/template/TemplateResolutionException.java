package com.relay.engine.template;

/** A template reference could not be resolved; the step fails with {@code template_unresolvable}. */
public class TemplateResolutionException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String expression;
  private final String field;

  /**
   * Creates the exception.
   *
   * @param message clear, caller-safe message
   * @param expression offending expression
   * @param field param path
   */
  public TemplateResolutionException(String message, String expression, String field) {
    super(message);
    this.expression = expression;
    this.field = field;
  }

  /** The offending expression. */
  public String expression() {
    return expression;
  }

  /** The param path, e.g. {@code params.to}. */
  public String field() {
    return field;
  }
}
