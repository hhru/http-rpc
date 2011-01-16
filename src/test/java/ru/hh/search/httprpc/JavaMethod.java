package ru.hh.search.httprpc;

public class JavaMethod implements ServerMethod<String, String> {
  
  public String call(Envelope envelope, String argument) {
    return argument.toUpperCase();
  }
}
