package com.relay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Relay entry point.
 *
 * <p>The same artifact runs as the HTTP API (profile {@code api}), as a worker (profile {@code
 * worker}, selected automatically when {@code WORKER_ONLY=true}), or as both in one JVM when no
 * profile is set (development convenience). The API never executes nodes; it only enqueues.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class RelayApplication {

  /**
   * Boots Relay.
   *
   * @param args standard Spring Boot arguments
   */
  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(RelayApplication.class);
    if ("true".equalsIgnoreCase(System.getenv("WORKER_ONLY"))) {
      app.setAdditionalProfiles("worker");
    }
    app.run(args);
  }
}
