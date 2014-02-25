package ru.hh.httprpc;

import io.netty.handler.codec.http.HttpResponseStatus;

public class RPCMethodException extends RuntimeException {

  private HttpResponseStatus status;
  private String message;

  public RPCMethodException(HttpResponseStatus status, String message) {
    super(message);
    this.status = status;
    this.message = message;
  }

  public RPCMethodException(HttpResponseStatus status, Throwable cause) {
    this(status, cause.getMessage(), cause);
  }

  public RPCMethodException(HttpResponseStatus status, String message, Throwable cause) {
    super(message, cause);
    this.status = status;
    this.message = message;
  }

  public HttpResponseStatus getStatus() {
    return status;
  }

  public String getMessage() {
    return message;
  }
}
