package ru.hh.search.httprpc;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.buffer.ChannelBuffers;

public class JavaSerializerFactory implements SerializerFactory {
  @SuppressWarnings("unchecked")
  public <T> Serializer<T> createForClass(Class<T> clazz) {
    return (Serializer<T>) INSTANCE;
  }

  private static final JavaSerializer INSTANCE = new JavaSerializer();

  private static class JavaSerializer<T> implements Serializer<T> {
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
        ObjectInputStream ois = new ObjectInputStream(new ChannelBufferInputStream(serialForm));
        return (T) ois.readObject();
      } catch (Exception e) {
        throw new SerializationException(e);
      }
    }
  }
}
