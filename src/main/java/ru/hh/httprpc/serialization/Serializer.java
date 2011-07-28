package ru.hh.httprpc.serialization;

import com.google.common.base.Function;
import org.jboss.netty.buffer.ChannelBuffer;

public interface Serializer<I, O> {
  String getContentType();

  Function<O, ChannelBuffer> encoder(Class<O> clazz);
  Function<ChannelBuffer, I> decoder(Class<I> clazz);
}
