package com.relay.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relay.api.error.ErrorResponse;
import com.relay.config.RelayProperties;
import com.relay.domain.error.ErrorCode;
import com.relay.security.crypto.EnvelopeCipher;
import com.relay.support.TraceContext;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless API security.
 *
 * <ul>
 *   <li>{@code /hooks/**} is open at this layer: "Webhook endpoint: the per-workflow secret in
 *       {@code X-Relay-Secret}. No other auth required on this route." (API_CONTRACT,
 *       Authentication). The controller checks the secret.
 *   <li>Everything else needs a bearer credential; 401 bodies use the uniform error shape.
 *   <li>Role separation is enforced with method guards ({@code @PreAuthorize}) on controllers.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication
@EnableMethodSecurity
public class SecurityConfig {

  /**
   * The filter chain.
   *
   * @param http security builder
   * @param bearer bearer-token filter
   * @param mapper Jackson mapper
   * @return the chain
   * @throws Exception on misconfiguration
   */
  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, BearerTokenAuthenticationFilter bearer, ObjectMapper mapper)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(HttpMethod.POST, "/hooks/**")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers("/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(bearer, UsernamePasswordAuthenticationFilter.class)
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) ->
                            write(
                                res,
                                mapper,
                                ErrorCode.UNAUTHORIZED,
                                "Missing or invalid platform token (Authorization: Bearer <token>)"))
                    .accessDeniedHandler(
                        (req, res, ex) ->
                            write(
                                res,
                                mapper,
                                ErrorCode.FORBIDDEN,
                                "Your token does not grant the role this action needs")));
    return http.build();
  }

  /**
   * Bearer filter (not registered as a servlet filter on its own; only inside the chain).
   *
   * @param props relay properties
   * @param jwtDecoder optional JWT decoder
   * @param apiTokens API token store
   * @return the filter
   */
  @Bean
  public BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter(
      RelayProperties props, Optional<JwtDecoder> jwtDecoder, ApiTokenStore apiTokens) {
    return new BearerTokenAuthenticationFilter(props.auth().demoToken(), jwtDecoder, apiTokens);
  }

  /**
   * Prevents Spring Boot from also registering the bearer filter for every request outside the
   * security chain.
   *
   * @param filter the filter
   * @return a disabled registration
   */
  @Bean
  public org.springframework.boot.web.servlet.FilterRegistrationBean<BearerTokenAuthenticationFilter>
      bearerFilterRegistration(BearerTokenAuthenticationFilter filter) {
    var reg = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
    reg.setEnabled(false);
    return reg;
  }

  /**
   * RS256 JWT decoder, only when a public key is configured.
   *
   * @param props relay properties
   * @return the decoder or null (bean absent)
   * @throws Exception if the key is malformed
   */
  @Bean
  @org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
      "'${relay.auth.jwt-public-key:}' != ''")
  public JwtDecoder jwtDecoder(RelayProperties props) throws Exception {
    String pem =
        props.auth().jwtPublicKey()
            .replace("\\n", "\n")
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
    RSAPublicKey key =
        (RSAPublicKey)
            KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pem)));
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withPublicKey(key).signatureAlgorithm(SignatureAlgorithm.RS256).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.auth().jwtIssuer()));
    return decoder;
  }

  /**
   * API token store.
   *
   * @param jdbc JDBC template
   * @param cipher envelope cipher
   * @param props relay properties
   * @return store
   */
  @Bean
  public ApiTokenStore apiTokenStore(
      JdbcTemplate jdbc, EnvelopeCipher cipher, RelayProperties props) {
    return new ApiTokenStore(jdbc, cipher, props.crypto().dbColumnKey());
  }

  private static void write(
      HttpServletResponse res, ObjectMapper mapper, ErrorCode code, String message)
      throws IOException {
    res.setStatus(code.httpStatus());
    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
    mapper.writeValue(
        res.getOutputStream(),
        ErrorResponse.of(message, code.code(), TraceContext.currentTraceId(), null));
  }
}
