package ru.hh.search.httprpc;

import java.io.InputStream;

public interface Serializer {
  String getContentType();
  <T> byte[] toBytes(T object);
  <T> T fromBytes(byte[] bytes, Class<T> klass);
  <T> T fromInputStream(InputStream stream, Class<T> klass);
}
