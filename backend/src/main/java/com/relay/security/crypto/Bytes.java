package com.relay.security.crypto;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/** Length-prefixed byte framing and digests used by the crypto layer. */
final class Bytes {

  private Bytes() {}

  /** Concatenates {@code [len:int32][bytes]} frames. */
  static byte[] frame(byte[]... parts) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (byte[] p : parts) {
      out.writeBytes(ByteBuffer.allocate(4).putInt(p.length).array());
      out.writeBytes(p);
    }
    return out.toByteArray();
  }

  /** Splits frames written by {@link #frame(byte[]...)}. */
  static List<byte[]> unframe(byte[] data) {
    List<byte[]> parts = new ArrayList<>();
    ByteBuffer buf = ByteBuffer.wrap(data);
    while (buf.hasRemaining()) {
      if (buf.remaining() < 4) {
        throw new CryptoException("truncated frame header");
      }
      int len = buf.getInt();
      if (len < 0 || len > buf.remaining()) {
        throw new CryptoException("invalid frame length");
      }
      byte[] p = new byte[len];
      buf.get(p);
      parts.add(p);
    }
    return parts;
  }

  /** Simple concatenation. */
  static byte[] concat(byte[]... parts) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (byte[] p : parts) {
      out.writeBytes(p);
    }
    return out.toByteArray();
  }

  /** SHA-256 digest. */
  static byte[] sha256(byte[] data) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(data);
    } catch (NoSuchAlgorithmException e) {
      throw new CryptoException("SHA-256 unavailable", e);
    }
  }
}
