package ru.hh.httprpc;

public class BadResponseException extends RuntimeException {
  final String details;

  public BadResponseException(String message, String details) {
    super(message);
    this.details = details;
  }

  public String getDetails() {
    return details;
  }
}
