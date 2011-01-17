package ru.hh.search.httprpc;

public interface Encoder<T>  {
  String getContentType();
  byte[] toBytes(T object);
}
