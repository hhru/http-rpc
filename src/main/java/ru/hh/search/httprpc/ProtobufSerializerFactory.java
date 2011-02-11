package ru.hh.search.httprpc;

import com.google.common.base.Throwables;
import com.google.protobuf.Message;
import java.lang.reflect.Method;

public class ProtobufSerializerFactory implements SerializerFactory{
  @SuppressWarnings({"unchecked"})
  @Override
  public <T> Serializer<T> createForClass(Class<T> clazz) {
    try {
      Method getDefaultInstance = clazz.getMethod("getDefaultInstance");
      return new ProtobufSerializer<T>((T)getDefaultInstance.invoke(null));
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }
}
