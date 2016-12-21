package ru.hh.httprpc.serialization;

import com.google.common.base.Function;
import com.google.common.base.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hh.httprpc.util.FastObjectInputStream;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.TimeUnit;

public class JavaBaseSerializer implements BaseSerializer<Object, Object> {
  private static final Logger logger = LoggerFactory.getLogger(JavaBaseSerializer.class);

  public String getContentType() {
    return "application/x-java-serialized-object";
  }

  public Function<Object, byte[]> encoder(Class<Object> clazz) {
    return ENCODER;
  }

  public Function<InputStream, Object> decoder(Class<Object> clazz) {
    return DECODER;
  }

  private static Function<Object, byte[]> ENCODER = new Function<Object, byte[]>() {
    public byte[] apply(Object object) {
      try (
          ByteArrayOutputStream bos = new ByteArrayOutputStream();
          ObjectOutputStream out = new ObjectOutputStream(bos);
      ) {
        Stopwatch timer = Stopwatch.createStarted();
        out.writeObject(object);
        out.flush();
        if (object != null) {
          logger.info(String.format(
              "java jetty serialize [%s], elapsed time: %d ms",
              object.getClass().getSimpleName(),
              timer.stop().elapsed(TimeUnit.MILLISECONDS)
          ));
        }
        return bos.toByteArray();
      } catch (Exception e) {
        throw new SerializationException(e);
      }
    }
  };

  private static Function<InputStream, Object> DECODER = new Function<InputStream, Object>() {
    public Object apply(InputStream serialForm) {
      if (serialForm == null) {
        return null;
      }
      try (ObjectInputStream in = new FastObjectInputStream(serialForm)) {
        Stopwatch timer = Stopwatch.createStarted();
        Object object = in.readObject();
        if (object != null) {
          logger.info(String.format(
              "java deserialize [%s], elapsed time: %d ms",
              object.getClass().getSimpleName(),
              timer.stop().elapsed(TimeUnit.MILLISECONDS)
          ));
        }
        return object;
      } catch (InvalidClassException | ClassNotFoundException e) {
        throw new VersionException(e);
      } catch (Exception e) {
        throw new SerializationException(e);
      }
    }
  };
}
