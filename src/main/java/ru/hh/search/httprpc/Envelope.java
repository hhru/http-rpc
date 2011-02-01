package ru.hh.search.httprpc;

import java.io.Serializable;

public class Envelope implements Serializable {
  public final long timeoutMilliseconds;
  public final String requestId;

  public Envelope(long timeoutMilliseconds, String requestId) {
    this.timeoutMilliseconds = timeoutMilliseconds;
    this.requestId = requestId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Envelope envelope = (Envelope) o;

    if (timeoutMilliseconds != envelope.timeoutMilliseconds) return false;
    if (!requestId.equals(envelope.requestId)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = (int) (timeoutMilliseconds ^ (timeoutMilliseconds >>> 32));
    result = 31 * result + (requestId != null ? requestId.hashCode() : 0);
    return result;
  }
}
