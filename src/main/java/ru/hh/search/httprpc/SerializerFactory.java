package ru.hh.search.httprpc;

public interface SerializerFactory {
  <T> Serializer<T> createForClass(Class<T> clazz);
}
