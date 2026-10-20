package com.relay.support;

import java.security.SecureRandom;

/**
 * Prefixed, time-sortable identifiers (ULID layout, lowercase Crockford base32).
 *
 * <p>Example: {@code run_01j9zq3k7m4x2c8v5n6b0t1r2w}. The first 10 characters encode the
 * millisecond timestamp, so ids sort by creation time — handy for trace listings.
 */
public final class Ids {

  private static final char[] ALPHABET = "0123456789abcdefghjkmnpqrstvwxyz".toCharArray();
  private static final SecureRandom RANDOM = new SecureRandom();

  private Ids() {}

  /** Returns a new run id ({@code run_…}). */
  public static String runId() {
    return "run_" + ulid();
  }

  /** Returns a new approval id ({@code apr_…}). */
  public static String approvalId() {
    return "apr_" + ulid();
  }

  /** Returns a new workflow id ({@code wf_…}) for definitions submitted without one. */
  public static String workflowId() {
    return "wf_" + ulid();
  }

  /** Returns a new trace id (32 lowercase hex characters, W3C trace-context compatible). */
  public static String traceId() {
    byte[] bytes = new byte[16];
    RANDOM.nextBytes(bytes);
    StringBuilder sb = new StringBuilder(32);
    for (byte b : bytes) {
      sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
    }
    return sb.toString();
  }

  /** Returns a 26-character lowercase ULID. */
  public static String ulid() {
    return ulid(System.currentTimeMillis());
  }

  @VisibleForTesting
  static String ulid(long epochMillis) {
    char[] out = new char[26];
    long time = epochMillis;
    for (int i = 9; i >= 0; i--) {
      out[i] = ALPHABET[(int) (time & 31)];
      time >>>= 5;
    }
    byte[] rnd = new byte[10];
    RANDOM.nextBytes(rnd);
    long hi = ((rnd[0] & 0xFFL) << 8) | (rnd[1] & 0xFFL);
    long lo = 0;
    for (int i = 2; i < 10; i++) {
      lo = (lo << 8) | (rnd[i] & 0xFFL);
    }
    // 80 random bits -> 16 base32 characters, least significant first.
    for (int i = 25; i >= 10; i--) {
      out[i] = ALPHABET[(int) (lo & 31)];
      lo = (lo >>> 5) | ((hi & 31) << 59);
      hi >>>= 5;
    }
    return new String(out);
  }
}
