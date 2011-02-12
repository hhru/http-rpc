package ru.hh.search.httprpc;

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.common.collect.MapMaker;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.util.concurrent.ConcurrentMap;
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
        ObjectInputStream ois = new FastObjectInputStream(new ChannelBufferInputStream(serialForm));
        return (T) ois.readObject();
      } catch (Exception e) {
        throw new SerializationException(e);
      }
    }

    // Todo: Use JBoss Marshalling/Serialization?
    private static class FastObjectInputStream extends ObjectInputStream {
      private static final ConcurrentMap<String, Class<?>> CLASS_CACHE = new MapMaker().
          concurrencyLevel(Runtime.getRuntime().availableProcessors()).
          makeComputingMap(new Function<String, Class<?>>() {
            public Class<?> apply(String input) {
              try {
                return Class.forName(input);
              } catch (ClassNotFoundException e) {
                throw Throwables.propagate(e);
              }
            }
          });

      public FastObjectInputStream(InputStream in) throws IOException {
        super(in);
      }

      protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
        return CLASS_CACHE.get(desc.getName());
      }
    }
  }
}
