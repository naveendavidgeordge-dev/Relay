package com.relay.security;

import java.util.List;

/**
 * Personas from API_CONTRACT "Personas", mapped to Spring Security roles. The demo token holds all
 * three ("For Must Have, a single demo token covering builder/operator/approver is fine").
 */
public final class Roles {

  /** Creates and publishes workflows. */
  public static final String BUILDER = "BUILDER";

  /** Watches runs, reads traces, triggers and cancels runs. */
  public static final String OPERATOR = "OPERATOR";

  /** Reviews and decides pending approvals. */
  public static final String APPROVER = "APPROVER";

  /** Every role. */
  public static final List<String> ALL = List.of(BUILDER, OPERATOR, APPROVER);

  private Roles() {}
}
