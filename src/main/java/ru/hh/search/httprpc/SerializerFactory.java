package ru.hh.search.httprpc;

public interface SerializerFactory<Base> {
  <T extends Base> Serializer<T> createForClass(Class<T> clazz);
}
