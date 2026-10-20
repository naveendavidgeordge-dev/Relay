package com.relay.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * An authenticated platform caller. The principal name is what approvals record as {@code
 * decided_by}. Credentials are never retained.
 */
public final class RelayAuthentication extends AbstractAuthenticationToken {

  private static final long serialVersionUID = 1L;

  private final String principal;
  private final String method;

  /**
   * Creates an authenticated token.
   *
   * @param principal caller identity (e.g. {@code demo}, a JWT {@code sub}, a token name)
   * @param roles role names without the {@code ROLE_} prefix
   * @param method how the caller authenticated ({@code demo-token}, {@code jwt}, {@code api-token})
   */
  public RelayAuthentication(String principal, Collection<String> roles, String method) {
    super(authorities(roles));
    this.principal = principal;
    this.method = method;
    setAuthenticated(true);
  }

  private static List<GrantedAuthority> authorities(Collection<String> roles) {
    return roles.stream()
        .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
        .toList();
  }

  @Override
  public Object getCredentials() {
    return null;
  }

  @Override
  public Object getPrincipal() {
    return principal;
  }

  /** How the caller authenticated. */
  public String method() {
    return method;
  }
}
