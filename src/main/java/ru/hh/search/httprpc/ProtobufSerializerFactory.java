package ru.hh.search.httprpc;

import com.google.common.base.Throwables;
import com.google.protobuf.Message;
import java.lang.reflect.Method;

public class ProtobufSerializerFactory implements SerializerFactory<Message>{
  @Override
  public <T extends Message> Serializer<T> createForClass(Class<T> clazz) {
    try {
      Method getDefaultInstance = clazz.getMethod("getDefaultInstance");
      return new ProtobufSerializer<T>((T)getDefaultInstance.invoke(null));
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }
}
