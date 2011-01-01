package ru.hh.search.httprpc;

import com.google.common.base.Throwables;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class JavaSerializer implements Serializer {
  @Override
  public String getContentType() {
    return "application/x-java-serialized-object";
  }

  @Override
  public <T> byte[] toBytes(T object) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream(500);
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(object);
      return baos.toByteArray();
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }

  @Override
  public <T> T fromBytes(byte[] bytes, Class<T> klass) {
    try {
      ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes));
      @SuppressWarnings("unchecked")
      T v = (T) ois.readObject();
      return v;
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }
}
