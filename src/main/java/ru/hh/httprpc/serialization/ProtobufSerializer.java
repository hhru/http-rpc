package ru.hh.httprpc.serialization;

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.protobuf.Message;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBuffers;

public class ProtobufSerializer implements Serializer {
  public String getContentType() {
    return "application/x-protobuf";
  }

  public <T> Function<T, ChannelBuffer> encoder(Class<T> clazz) {
    return new Function<T, ChannelBuffer>() {
      public ChannelBuffer apply(T object) {
        try {
          return ChannelBuffers.wrappedBuffer(((Message) object).toByteArray());
        } catch (Exception e) {
          throw new SerializationException(e);
        }
      }
    };
  }

  @SuppressWarnings({"unchecked"})
  public <T> Function<ChannelBuffer, T> decoder(Class<T> clazz) {
    final Message prototype;
    try {
      prototype = (Message) clazz.getMethod("getDefaultInstance").invoke(null);
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }

    return new Function<ChannelBuffer, T>() {
      public T apply(ChannelBuffer serialForm) {
        try {
          if (serialForm.hasArray())
            return (T) prototype.newBuilderForType().mergeFrom(serialForm.array(), serialForm.arrayOffset(), serialForm.readableBytes()).build();
          else
            return (T) prototype.newBuilderForType().mergeFrom(new ChannelBufferInputStream(serialForm)).build();
        } catch (Exception e) {
          throw new SerializationException(e);
        }
      }
    };
  }
}
