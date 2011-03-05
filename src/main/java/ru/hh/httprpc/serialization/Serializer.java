package ru.hh.httprpc.serialization;

import com.google.common.base.Function;
import org.jboss.netty.buffer.ChannelBuffer;

public interface Serializer {
  String getContentType();

  <T> Function<T, ChannelBuffer> encoder(Class<T> clazz);
  <T> Function<ChannelBuffer, T> decoder(Class<T> clazz);
}
