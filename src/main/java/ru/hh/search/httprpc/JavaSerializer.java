package ru.hh.search.httprpc;

import com.google.common.base.Throwables;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class JavaSerializer<T> implements Serializer<T> {
  @Override
  public String getContentType() {
    return "application/x-java-serialized-object";
  }

  @Override
  public byte[] toBytes(T object) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(object);
      return baos.toByteArray();
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }

  @Override
  public T fromBytes(byte[] bytes, int offset, int length) {
    return fromInputStream(new ByteArrayInputStream(bytes, offset, length));
  }

  @Override
  public T fromInputStream(InputStream stream) {
    try {
      ObjectInputStream ois = new ObjectInputStream(stream);
      @SuppressWarnings("unchecked")
      T v = (T) ois.readObject();
      return v;
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }
}
