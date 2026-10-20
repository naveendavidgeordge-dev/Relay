package com.relay.security.crypto;

/** A cryptographic operation failed. Messages never contain key material or plaintext. */
public class CryptoException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param message safe description
   * @param cause underlying cause
   */
  public CryptoException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Creates the exception.
   *
   * @param message safe description
   */
  public CryptoException(String message) {
    super(message);
  }
}
