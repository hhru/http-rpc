package ru.hh.search.httprpc;

public class Envelope {
  public final int timeout;
  public final String requestId;

  public Envelope(int timeout, String requestId) {
    this.timeout = timeout;
    this.requestId = requestId;
  }
}
