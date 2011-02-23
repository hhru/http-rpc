package ru.hh.httprpc;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import ru.hh.httprpc.util.FastObjectInputStream;

public class JavaSerializer implements Serializer {
  @SuppressWarnings("unchecked")
  public <T> ForClass<T> forClass(Class<T> clazz) {
    return (ForClass<T>) INSTANCE;
  }

  private static final ForClass INSTANCE = new ForClass();

  private static class ForClass<T> implements Serializer.ForClass<T> {
    public String getContentType() {
      return "application/x-java-serialized-object";
    }

    public ChannelBuffer serialize(T object) throws SerializationException {
      try {
        ChannelBuffer serialForm = ChannelBuffers.dynamicBuffer();
        ObjectOutputStream oos = new ObjectOutputStream(new ChannelBufferOutputStream(serialForm));
        oos.writeObject(object);
        return serialForm;
      } catch (Exception e) {
        throw new SerializationException(e);
      }
    }

    @SuppressWarnings("unchecked")
    public T deserialize(ChannelBuffer serialForm) throws SerializationException {
      try {
        // Todo: Use JBoss Marshalling/Serialization?
        ObjectInputStream ois = new FastObjectInputStream(new ChannelBufferInputStream(serialForm));
        return (T) ois.readObject();
      } catch (Exception e) {
        throw new SerializationException(e);
      }
    }
  }
}
