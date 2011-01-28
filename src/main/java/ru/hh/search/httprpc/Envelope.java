package ru.hh.search.httprpc;

public class Envelope {
  public final int timeout;
  public final String requestId;

  public Envelope(int timeout, String requestId) {
    this.timeout = timeout;
    this.requestId = requestId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Envelope envelope = (Envelope) o;

    if (timeout != envelope.timeout) return false;
    if (!requestId.equals(envelope.requestId)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = timeout;
    result = 31 * result + requestId.hashCode();
    return result;
  }
}
