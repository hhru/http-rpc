package ru.hh.httprpc.util.netty;

import io.netty.buffer.ByteBuf;

public class ByteBufUtil {
  public static byte[] bufferBytes(ByteBuf buf) {
    byte[] bytes = new byte[buf.readableBytes()];
    int readerIndex = buf.readerIndex();
    buf.getBytes(readerIndex, bytes);
    return bytes;
  }
}
