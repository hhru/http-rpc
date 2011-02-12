package ru.hh.search.httprpc;

import com.google.common.base.Throwables;
import com.google.protobuf.Message;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBuffers;

public class ProtobufSerializerFactory implements SerializerFactory{
  @SuppressWarnings({"unchecked"})
  public <T> Serializer<T> createForClass(Class<T> clazz) {
    try {
      return new ProtobufSerializer<T>((Message) clazz.getMethod("getDefaultInstance").invoke(null));
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }

  private static class ProtobufSerializer<T> implements Serializer<T> {
    private final Message prototype;

    public ProtobufSerializer(Message prototype) {
      this.prototype = prototype;
    }

    public String getContentType() {
      return "application/x-protobuf";
    }

    public ChannelBuffer serialize(T object) throws SerializationException {
      try {
        return ChannelBuffers.wrappedBuffer(((Message) object).toByteArray());
      } catch (Exception e) {
        throw new SerializationException(e);
      }
    }

    @SuppressWarnings("unchecked")
    public T deserialize(ChannelBuffer serialForm) throws SerializationException {
      try {
        if (serialForm.hasArray())
          return (T) prototype.newBuilderForType().mergeFrom(serialForm.array(), serialForm.arrayOffset(), serialForm.readableBytes()).build();
        else
          return (T) prototype.newBuilderForType().mergeFrom(new ChannelBufferInputStream(serialForm)).build();
      } catch (Exception e) {
        throw new SerializationException(e);
      }
    }
  }
}
