package ru.hh.search.httprpc;

public class JavaSerializerFactory implements SerializerFactory<Object> {
  @Override
  public <T> Serializer<T> createForClass(Class<T> clazz) {
    return new JavaSerializer<T>();
  }
}
