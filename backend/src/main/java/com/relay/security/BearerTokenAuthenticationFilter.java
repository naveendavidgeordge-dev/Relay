package com.relay.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates {@code Authorization: Bearer <token>} in this order:
 *
 * <ol>
 *   <li>the demo token ({@code RELAY_DEMO_TOKEN}) — all roles; "a simple demo token throughout" is
 *       acceptable (Case Study, Acceptable Simplifications) and the smoke test sends it;
 *   <li>an RS256 JWT, when {@code RELAY_JWT_PUBLIC_KEY} is configured — roles from the {@code
 *       roles} claim;
 *   <li>a platform API token from {@code api_tokens} (looked up by SHA-256 digest).
 * </ol>
 *
 * Unauthenticated requests continue down the chain and are rejected with 401 by the entry point.
 * Tokens are never logged.
 */
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(BearerTokenAuthenticationFilter.class);

  private final byte[] demoToken;
  private final Optional<JwtDecoder> jwtDecoder;
  private final ApiTokenStore apiTokens;

  /**
   * Creates the filter.
   *
   * @param demoToken configured demo token (blank disables it)
   * @param jwtDecoder RS256 decoder, if configured
   * @param apiTokens platform API token store
   */
  public BearerTokenAuthenticationFilter(
      String demoToken, Optional<JwtDecoder> jwtDecoder, ApiTokenStore apiTokens) {
    this.demoToken =
        demoToken == null || demoToken.isBlank() ? null : demoToken.getBytes(StandardCharsets.UTF_8);
    this.jwtDecoder = jwtDecoder;
    this.apiTokens = apiTokens;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
      String token = header.substring(7).strip();
      authenticate(token).ifPresent(a -> SecurityContextHolder.getContext().setAuthentication(a));
    }
    chain.doFilter(request, response);
  }

  private Optional<RelayAuthentication> authenticate(String token) {
    if (token.isEmpty()) {
      return Optional.empty();
    }
    if (demoToken != null
        && MessageDigest.isEqual(demoToken, token.getBytes(StandardCharsets.UTF_8))) {
      return Optional.of(new RelayAuthentication("demo", Roles.ALL, "demo-token"));
    }
    if (jwtDecoder.isPresent() && token.chars().filter(c -> c == '.').count() == 2) {
      try {
        Jwt jwt = jwtDecoder.get().decode(token);
        return Optional.of(new RelayAuthentication(jwt.getSubject(), roles(jwt), "jwt"));
      } catch (JwtException e) {
        log.debug("JWT rejected: {}", e.getClass().getSimpleName());
        return Optional.empty();
      }
    }
    return apiTokens
        .lookup(token)
        .map(t -> new RelayAuthentication("token:" + t.name(), t.roles(), "api-token"));
  }

  private static Collection<String> roles(Jwt jwt) {
    List<String> claim = jwt.getClaimAsStringList("roles");
    if (claim == null) {
      return List.of();
    }
    return claim.stream()
        .map(r -> r.toUpperCase(java.util.Locale.ROOT))
        .filter(Roles.ALL::contains)
        .toList();
  }
}
