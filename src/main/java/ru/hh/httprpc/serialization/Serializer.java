package ru.hh.httprpc.serialization;

import com.google.common.base.Function;
import io.netty.buffer.ByteBuf;

public interface Serializer<I, O> {
  String getContentType();

  Function<O, ByteBuf> encoder(Class<O> clazz);
  Function<ByteBuf, I> decoder(Class<I> clazz);
}
