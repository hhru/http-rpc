package ru.hh.search.httprpc;

public interface Serializer {
  String getContentType();
  <T> byte[] toBytes(T object);
  <T> T fromBytes(byte[] bytes, Class<T> klass);
}
