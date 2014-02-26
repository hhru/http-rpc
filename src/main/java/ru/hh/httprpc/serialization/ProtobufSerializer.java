package ru.hh.httprpc.serialization;

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.protobuf.Message;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayInputStream;
import static ru.hh.httprpc.util.netty.ByteBufUtil.bufferBytes;

public class ProtobufSerializer implements Serializer<Message,Message> {
  @Override
  public String getContentType() {
    return "application/x-protobuf";
  }

  @Override
  public Function<Message, ByteBuf> encoder(Class<Message> clazz) {
    return new Function<Message, ByteBuf>() {
      public ByteBuf apply(Message object) {
        try {
          return Unpooled.wrappedBuffer(object.toByteArray());
        } catch (Exception e) {
          throw new SerializationException(e);
        }
      }
    };
  }

  @Override
  public Function<ByteBuf, Message> decoder(Class<Message> clazz) {
    final Message prototype;
    try {
      prototype = (Message) clazz.getMethod("getDefaultInstance").invoke(null);
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }

    return new Function<ByteBuf, Message>() {
      public Message apply(ByteBuf serialForm) {
        try {
          if (serialForm.hasArray())
            return prototype.newBuilderForType().mergeFrom(bufferBytes(serialForm), serialForm.arrayOffset(), serialForm.readableBytes()).build();
          else
            return prototype.newBuilderForType().mergeFrom(new ByteArrayInputStream(bufferBytes(serialForm))).build();
        } catch (Exception e) {
          throw new SerializationException(e);
        }
      }
    };
  }
}
