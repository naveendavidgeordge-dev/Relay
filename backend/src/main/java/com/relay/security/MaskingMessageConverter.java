package com.relay.security;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/** Logback {@code %maskedMsg}: the formatted message passed through {@link SensitiveDataMasker}. */
public class MaskingMessageConverter extends MessageConverter {

  @Override
  public String convert(ILoggingEvent event) {
    return SensitiveDataMasker.mask(super.convert(event));
  }
}
