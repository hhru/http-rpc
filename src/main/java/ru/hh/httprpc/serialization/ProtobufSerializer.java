package ru.hh.httprpc.serialization;

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.protobuf.Message;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBuffers;

public class ProtobufSerializer implements Serializer<Message,Message> {
  public String getContentType() {
    return "application/x-protobuf";
  }

  public Function<Message, ChannelBuffer> encoder(Class<Message> clazz) {
    return new Function<Message, ChannelBuffer>() {
      public ChannelBuffer apply(Message object) {
        try {
          return ChannelBuffers.wrappedBuffer(object.toByteArray());
        } catch (Exception e) {
          throw new SerializationException(e);
        }
      }
    };
  }

  @SuppressWarnings({"unchecked"})
  public Function<ChannelBuffer, Message> decoder(Class<Message> clazz) {
    final Message prototype;
    try {
      prototype = (Message) clazz.getMethod("getDefaultInstance").invoke(null);
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }

    return new Function<ChannelBuffer, Message>() {
      public Message apply(ChannelBuffer serialForm) {
        try {
          if (serialForm.hasArray())
            return prototype.newBuilderForType().mergeFrom(serialForm.array(), serialForm.arrayOffset(), serialForm.readableBytes()).build();
          else
            return prototype.newBuilderForType().mergeFrom(new ChannelBufferInputStream(serialForm)).build();
        } catch (Exception e) {
          throw new SerializationException(e);
        }
      }
    };
  }
}
