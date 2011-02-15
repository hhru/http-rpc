package ru.hh.search.httprpc;

import com.google.common.base.Throwables;
import com.google.protobuf.Message;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBuffers;

public class ProtobufSerializer implements Serializer {
  @SuppressWarnings({"unchecked"})
  public <T> ForClass<T> forClass(Class<T> clazz) {
    try {
      return new ForClass<T>((Message) clazz.getMethod("getDefaultInstance").invoke(null));
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }

  private static class ForClass<T> implements Serializer.ForClass<T> {
    private final Message prototype;

    public ForClass(Message prototype) {
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
