package com.relay.config;

import com.relay.domain.catalog.NodeCatalog;
import com.relay.domain.validation.PublishValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the pure-domain {@link PublishValidator} with configured severities. */
@Configuration(proxyBeanMethods = false)
public class ValidationConfig {

  /**
   * The publish validator.
   *
   * @param catalog node catalog
   * @param props relay properties
   * @return validator
   */
  @Bean
  public PublishValidator publishValidator(NodeCatalog catalog, RelayProperties props) {
    boolean reject =
        props.validation() != null
            && props.validation().cyclicEntry() == RelayProperties.Severity.REJECT;
    return new PublishValidator(catalog, reject);
  }
}
