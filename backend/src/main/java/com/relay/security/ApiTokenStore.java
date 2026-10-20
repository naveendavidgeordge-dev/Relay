package com.relay.security;

import com.relay.security.crypto.EnvelopeCipher;
import com.relay.support.Ids;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Platform API tokens over {@code api_tokens}: "hashed + encrypted". Lookup compares the pgcrypto
 * {@code digest(token, 'sha256')} — the clear token is never stored or needed. The envelope copy
 * exists for audit and controlled re-issue only.
 */
public class ApiTokenStore {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final JdbcTemplate jdbc;
  private final EnvelopeCipher cipher;
  private final String columnKey;

  /**
   * Creates the store.
   *
   * @param jdbc JDBC template
   * @param cipher envelope cipher
   * @param columnKey pgcrypto column key
   */
  public ApiTokenStore(JdbcTemplate jdbc, EnvelopeCipher cipher, String columnKey) {
    this.jdbc = jdbc;
    this.cipher = cipher;
    this.columnKey = columnKey;
  }

  /**
   * Issues a token. The clear value is returned exactly once.
   *
   * @param name label
   * @param roles granted roles
   * @return the clear token ({@code rly_…})
   */
  public String issue(String name, List<String> roles) {
    byte[] raw = new byte[32];
    RANDOM.nextBytes(raw);
    String token = "rly_" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    String id = "tok_" + Ids.ulid();
    byte[] envelope = cipher.seal(token.getBytes(StandardCharsets.UTF_8), "api_token:" + id);
    jdbc.update(
        """
        INSERT INTO api_tokens (id, name, token_sha256, token_envelope, kem_key_id, roles)
        VALUES (?, ?, digest(?, 'sha256'), pgp_sym_encrypt_bytea(?, ?), ?, ?)
        """,
        id,
        name,
        token,
        envelope,
        columnKey,
        cipher.keyIdOf(envelope),
        String.join(",", roles));
    return token;
  }

  /**
   * Finds an active (non-revoked, non-expired) token.
   *
   * @param token presented token
   * @return the token's name and roles
   */
  public Optional<Token> lookup(String token) {
    if (!token.startsWith("rly_")) {
      return Optional.empty();
    }
    List<Token> rows =
        jdbc.query(
            """
            SELECT name, roles FROM api_tokens
             WHERE token_sha256 = digest(?, 'sha256')
               AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at > now())
            """,
            (rs, i) ->
                new Token(
                    rs.getString("name"),
                    Arrays.stream(rs.getString("roles").split(","))
                        .map(String::strip)
                        .filter(Roles.ALL::contains)
                        .toList()),
            token);
    return rows.stream().findFirst();
  }

  /**
   * A resolved token.
   *
   * @param name label
   * @param roles granted roles
   */
  public record Token(String name, List<String> roles) {}
}
