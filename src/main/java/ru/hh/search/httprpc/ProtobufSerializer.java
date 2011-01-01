package ru.hh.search.httprpc;

import com.google.common.base.Throwables;
import com.google.protobuf.Message;
import java.io.InputStream;
import java.lang.reflect.Method;

public class ProtobufSerializer implements Serializer {
  @Override
  public String getContentType() {
    return "application/x-protobuf";
  }

  @Override
  public <T> T fromInputStream(InputStream stream, Class<T> klass) {
    try {
      // TODO: don't use reflection
      Method newBuilder = klass.getMethod("newBuilder");
      Message.Builder builder = (Message.Builder) newBuilder.invoke(null);
      return (T) builder.mergeFrom(stream).build();
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }

  @Override
  public <T> byte[] toBytes(T object) {
      return ((Message)object).toByteArray();
  }

  @Override
  public <T> T fromBytes(byte[] bytes, Class<T> klass) {
    try {
      // TODO: don't use reflection
      Method newBuilder = klass.getMethod("newBuilder");
      Message.Builder builder = (Message.Builder) newBuilder.invoke(null);
      return (T) builder.mergeFrom(bytes).build();
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }
}
