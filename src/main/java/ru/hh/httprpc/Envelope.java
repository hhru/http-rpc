package ru.hh.httprpc;

import java.io.Serializable;

public class Envelope implements Serializable {
  public static final long DEFAULT_TIMEOUT = -1;
  public static final String DEFAULT_REQUESTID = "-noRequestId-";

  public final long timeoutMillis;
  public final String requestId;

  public Envelope() {
    this(DEFAULT_TIMEOUT, DEFAULT_REQUESTID);
  }

  public Envelope(long timeoutMillis, String requestId) {
    this.timeoutMillis = timeoutMillis;
    this.requestId = requestId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Envelope envelope = (Envelope) o;

    if (timeoutMillis != envelope.timeoutMillis) return false;
    if (!requestId.equals(envelope.requestId)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = (int) (timeoutMillis ^ (timeoutMillis >>> 32));
    result = 31 * result + (requestId != null ? requestId.hashCode() : 0);
    return result;
  }
}
