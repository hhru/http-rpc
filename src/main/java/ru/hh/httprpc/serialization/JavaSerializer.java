package ru.hh.httprpc.serialization;

import com.google.common.base.Function;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import ru.hh.httprpc.util.FastObjectInputStream;
import static ru.hh.httprpc.util.netty.ByteBufUtil.bufferBytes;

public class JavaSerializer implements Serializer<Object, Object> {
  @Override
  public String getContentType() {
    return "application/x-java-serialized-object";
  }

  @Override
  public Function<Object, ByteBuf> encoder(Class<Object> clazz) {
    return ENCODER;
  }

  @Override
  public Function<ByteBuf, Object> decoder(Class<Object> clazz) {
    return DECODER;
  }

  private static Function<Object, ByteBuf> ENCODER = new Function<Object, ByteBuf>() {
    public ByteBuf apply(Object object) {
      try {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(object);
        return Unpooled.wrappedBuffer(baos.toByteArray());
      } catch (Exception e) {
        throw new SerializationException(e);
      }
    }
  };

  private static Function<ByteBuf, Object> DECODER = new Function<ByteBuf, Object>() {
    public Object apply(ByteBuf serialForm) {
      try {
        // Todo: Use JBoss Marshalling/Serialization?
        ByteArrayInputStream bais = new ByteArrayInputStream(bufferBytes(serialForm));
        ObjectInputStream ois = new FastObjectInputStream(bais);
        return ois.readObject();
      } catch (Exception e) {
        throw new SerializationException(e);
      }
    }
  };
}
