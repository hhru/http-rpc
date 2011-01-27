package ru.hh.search.httprpc;

import java.io.InputStream;

public interface Serializer<T> {
  String getContentType();
  byte[] toBytes(T object);
  T fromBytes(byte[] bytes, int offset, int length);
  T fromInputStream(InputStream stream);
}
