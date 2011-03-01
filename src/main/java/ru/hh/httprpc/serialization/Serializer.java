package ru.hh.httprpc.serialization;

import org.jboss.netty.buffer.ChannelBuffer;

public interface Serializer {
  interface ForClass<T> {
    String getContentType();

    ChannelBuffer serialize(T object) throws SerializationException;

    T deserialize(ChannelBuffer serialForm) throws SerializationException;
  }

  <T> ForClass<T> forClass(Class<T> clazz);
}
