package com.relay.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Resolves the acting principal for audit fields such as {@code decided_by}. */
public final class CurrentActor {

  private CurrentActor() {}

  /** The authenticated principal name, or {@code anonymous}. */
  public static String name() {
    Authentication a = SecurityContextHolder.getContext().getAuthentication();
    return a == null || a.getName() == null ? "anonymous" : a.getName();
  }
}
