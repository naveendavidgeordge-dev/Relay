package com.relay.config;

import com.relay.security.crypto.BouncyCastlePqCryptoService;
import com.relay.security.crypto.EnvelopeCipher;
import com.relay.security.crypto.KeyRing;
import com.relay.security.crypto.PayloadSigner;
import com.relay.security.crypto.PqCryptoService;
import com.relay.security.crypto.PqPayloadSigner;
import com.relay.security.crypto.TestPqCryptoService;
import com.relay.security.secrets.JdbcWebhookSecretStore;
import com.relay.security.secrets.WebhookSecretStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** Wires the crypto adapters. {@code relay.crypto.provider=test} selects the deterministic fake. */
@Configuration(proxyBeanMethods = false)
public class CryptoConfig {

  /**
   * Primitive provider.
   *
   * @param props relay properties
   * @return BouncyCastle (default) or the test implementation
   */
  @Bean
  public PqCryptoService pqCryptoService(RelayProperties props) {
    return "test".equalsIgnoreCase(props.crypto().provider())
        ? new TestPqCryptoService()
        : new BouncyCastlePqCryptoService();
  }

  /**
   * Key ring.
   *
   * @param jdbc JDBC template
   * @param pq primitives
   * @param props relay properties
   * @return key ring
   */
  @Bean
  public KeyRing keyRing(JdbcTemplate jdbc, PqCryptoService pq, RelayProperties props) {
    return new KeyRing(jdbc, pq, props.crypto().masterKey());
  }

  /**
   * Envelope cipher.
   *
   * @param keys key ring
   * @param pq primitives
   * @return cipher
   */
  @Bean
  public EnvelopeCipher envelopeCipher(KeyRing keys, PqCryptoService pq) {
    return new EnvelopeCipher(keys, pq);
  }

  /**
   * ML-DSA signer.
   *
   * @param keys key ring
   * @param pq primitives
   * @return signer
   */
  @Bean
  public PayloadSigner payloadSigner(KeyRing keys, PqCryptoService pq) {
    return new PqPayloadSigner(keys, pq);
  }

  /**
   * Webhook secret store.
   *
   * @param jdbc JDBC template
   * @param cipher envelope cipher
   * @param props relay properties
   * @return store
   */
  @Bean
  public WebhookSecretStore webhookSecretStore(
      JdbcTemplate jdbc, EnvelopeCipher cipher, RelayProperties props) {
    return new JdbcWebhookSecretStore(jdbc, cipher, props.crypto().dbColumnKey());
  }
}
