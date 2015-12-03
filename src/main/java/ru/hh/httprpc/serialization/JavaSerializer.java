package ru.hh.httprpc.serialization;

import com.google.common.base.Function;
import com.google.common.base.Stopwatch;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.buffer.EmptyChannelBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hh.httprpc.util.FastObjectInputStream;

import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.TimeUnit;

public class JavaSerializer implements Serializer<Object, Object> {
  private static final Logger logger = LoggerFactory.getLogger(JavaSerializer.class);

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
        Stopwatch timer = Stopwatch.createStarted();
        ChannelBuffer serialForm = ChannelBuffers.dynamicBuffer();
        ObjectOutputStream oos = new ObjectOutputStream(new ChannelBufferOutputStream(serialForm));
        oos.writeObject(object);
        if (object != null) {
          logger.info(String.format("java serialize [%s], elapsed time: %d ms",
              object.getClass().getSimpleName(),
              timer.stop().elapsed(TimeUnit.MILLISECONDS)));
        }
        return serialForm;
      } catch (Exception e) {
        throw new SerializationException(e);
      }
    }
  };

  private static Function<ChannelBuffer, Object> DECODER = new Function<ChannelBuffer, Object>() {
    public Object apply(ChannelBuffer serialForm) {
      try {
        if (serialForm instanceof EmptyChannelBuffer) {
          return null;
        }
        Stopwatch timer = Stopwatch.createStarted();
        // Todo: Use JBoss Marshalling/Serialization?
        ObjectInputStream ois = new FastObjectInputStream(new ChannelBufferInputStream(serialForm));
        Object deserializedObject = ois.readObject();
        if (deserializedObject != null) {
          logger.info(String.format("java deserialize [%s], elapsed time: %d ms",
              deserializedObject.getClass().getSimpleName(),
              timer.stop().elapsed(TimeUnit.MILLISECONDS)));
        }
        return deserializedObject;
      } catch (InvalidClassException | ClassNotFoundException e) {
        throw new VersionException(e);
      } catch (Exception e) {
        throw new SerializationException(e);
      }
    }
  };
}
