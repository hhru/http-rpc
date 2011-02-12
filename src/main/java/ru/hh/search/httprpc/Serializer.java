package ru.hh.search.httprpc;

import org.jboss.netty.buffer.ChannelBuffer;

public interface Serializer<T> {
  String getContentType();

  ChannelBuffer serialize(T object) throws SerializationException;
  T deserialize(ChannelBuffer serialForm) throws SerializationException;
}
