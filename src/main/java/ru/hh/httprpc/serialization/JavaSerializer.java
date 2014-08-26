package ru.hh.httprpc.serialization;

import com.google.common.base.Function;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import ru.hh.httprpc.util.FastObjectInputStream;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class JavaSerializer implements Serializer<Object, Object> {
  public String getContentType() {
    return "application/x-java-serialized-object";
  }

  public Function<Object, ChannelBuffer> encoder(Class<Object> clazz) {
    return ENCODER;
  }

  public Function<ChannelBuffer, Object> decoder(Class<Object> clazz) {
    return DECODER;
  }

  private static Function<Object, ChannelBuffer> ENCODER = new Function<Object, ChannelBuffer>() {
    public ChannelBuffer apply(Object object) {
      try {
        ChannelBuffer serialForm = ChannelBuffers.dynamicBuffer();
        ObjectOutputStream oos = new ObjectOutputStream(new ChannelBufferOutputStream(serialForm));
        oos.writeObject(object);
        return serialForm;
      } catch (Exception e) {
        throw new SerializationException(e);
      }
    }
  };

  private static Function<ChannelBuffer, Object> DECODER = new Function<ChannelBuffer, Object>() {
    public Object apply(ChannelBuffer serialForm) {
      try {
        // Todo: Use JBoss Marshalling/Serialization?
        ObjectInputStream ois = new FastObjectInputStream(new ChannelBufferInputStream(serialForm));
        return ois.readObject();
      } catch (Exception e) {
        throw new SerializationException(e);
      }
    }
  };
}
