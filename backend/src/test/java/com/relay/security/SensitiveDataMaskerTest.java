package com.relay.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Satisfies: "webhook secrets and any model API keys never appear in traces … or error messages." */
class SensitiveDataMaskerTest {

  @Test
  void masksWebhookSecrets() {
    assertThat(SensitiveDataMasker.mask("secret is whsec_triage_501 ok"))
        .isEqualTo("secret is **** ok");
  }

  @Test
  void masksBearerTokensButKeepsTheScheme() {
    assertThat(SensitiveDataMasker.mask("Authorization: Bearer relay-demo-token"))
        .isEqualTo("Authorization: Bearer ****");
  }

  @Test
  void masksRelaySecretHeader() {
    assertThat(SensitiveDataMasker.mask("X-Relay-Secret: abc123")).doesNotContain("abc123");
  }

  @Test
  void masksProviderKeysAndJwts() {
    assertThat(SensitiveDataMasker.mask("key=gsk_abcdefghijklmnopqrstuvwx")).doesNotContain("gsk_abc");
    assertThat(SensitiveDataMasker.mask("AIzaSyA1234567890abcdefghijklmnopqrstu"))
        .isEqualTo("****");
    assertThat(SensitiveDataMasker.mask("t=eyJhbGciOi.eyJzdWIiOi.c2lnbmF0dXJl")).doesNotContain("eyJ");
  }

  @Test
  void masksKeyValuePairs() {
    assertThat(SensitiveDataMasker.mask("{\"api_key\": \"hunter2\"}")).doesNotContain("hunter2");
    assertThat(SensitiveDataMasker.mask("password=hunter2")).doesNotContain("hunter2");
  }

  @Test
  void leavesOrdinaryTextAlone() {
    String s = "run run_01j9 node skeleton_loading_states succeeded in 12ms (tokens_prompt=42)";
    assertThat(SensitiveDataMasker.mask(s)).isEqualTo(s);
  }
}
