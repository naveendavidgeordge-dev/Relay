package com.relay.domain.schedule;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.scheduling.support.CronExpression;

/**
 * 5-field cron ({@code minute hour day-of-month month day-of-week}) as used by the catalog's {@code
 * schedule} trigger, evaluated in UTC with 1-minute granularity ("1-minute granularity is
 * acceptable" — node_catalog.json). Internally delegates to Spring's 6-field {@link
 * CronExpression} with seconds pinned to 0.
 */
public final class CronSchedule {

  private final String expression;
  private final CronExpression cron;

  private CronSchedule(String expression, CronExpression cron) {
    this.expression = expression;
    this.cron = cron;
  }

  /**
   * Parses a 5-field cron.
   *
   * @param expression e.g. {@code *&#47;5 * * * *}
   * @return the schedule
   * @throws IllegalArgumentException if invalid
   */
  public static CronSchedule parse(String expression) {
    String trimmed = expression == null ? "" : expression.strip();
    String[] fields = trimmed.split("\\s+");
    if (fields.length != 5) {
      throw new IllegalArgumentException("expected 5 fields, got " + fields.length);
    }
    return new CronSchedule(trimmed, CronExpression.parse("0 " + trimmed));
  }

  /**
   * Explains why an expression is invalid.
   *
   * @param expression candidate
   * @return a problem description, or empty when valid
   */
  public static Optional<String> problem(String expression) {
    try {
      parse(expression);
      return Optional.empty();
    } catch (IllegalArgumentException e) {
      return Optional.of(e.getMessage());
    }
  }

  /**
   * Next fire time strictly after {@code after}, truncated to the minute.
   *
   * @param after reference instant
   * @return next fire instant
   */
  public Instant nextAfter(Instant after) {
    ZonedDateTime next = cron.next(after.atZone(ZoneOffset.UTC));
    if (next == null) {
      throw new IllegalStateException("cron '" + expression + "' never fires");
    }
    return next.toInstant().truncatedTo(ChronoUnit.MINUTES);
  }

  /** The original 5-field expression. */
  public String expression() {
    return expression;
  }
}
