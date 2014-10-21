package ru.hh.httprpc;

import org.jboss.netty.handler.codec.http.HttpResponseStatus;

public class BadResponseException extends RuntimeException {
  private final String details;
  private final HttpResponseStatus status;

  public BadResponseException(String message, String details, HttpResponseStatus status) {
    super(message);
    this.details = details;
    this.status = status;
  }

  public String getDetails() {
    return details;
  }

  public HttpResponseStatus getStatus() {
    return status;
  }
}
