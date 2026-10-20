package com.relay.config;

import com.relay.engine.template.TemplateResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Engine beans that are plain (framework-free) classes. */
@Configuration(proxyBeanMethods = false)
public class EngineConfig {

  /** The template resolver (stateless). */
  @Bean
  public TemplateResolver templateResolver() {
    return new TemplateResolver();
  }
}
