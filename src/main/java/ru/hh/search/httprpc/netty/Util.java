package ru.hh.search.httprpc.netty;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import ru.hh.search.httprpc.Serializer;

public final class Util {
  private Util() {
  }

  public static <T> T decodeContent(Serializer<T> decoder, ChannelBuffer content) {
    if (content.hasArray()) {
      return decoder.fromBytes(content.array(), content.arrayOffset(), content.readableBytes());
    } else {
      return decoder.fromInputStream(new ChannelBufferInputStream(content));
    }
  }
}
