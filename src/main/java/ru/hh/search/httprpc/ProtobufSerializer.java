package ru.hh.search.httprpc;

import com.google.common.base.Throwables;
import com.google.protobuf.Message;
import java.io.InputStream;

public class ProtobufSerializer<T> implements Serializer<T> {
  
  private final T prototype;

  public ProtobufSerializer(T prototype) {
    this.prototype = prototype;
  }

  @Override
  public String getContentType() {
    return "application/x-protobuf";
  }

  @SuppressWarnings({"unchecked"})
  @Override
  public T fromInputStream(InputStream stream) {
    try {
      return (T) ((Message)prototype).newBuilderForType().mergeFrom(stream).build();
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }

  @Override
  public byte[] toBytes(T object) {
      return ((Message)object).toByteArray();
  }

  @SuppressWarnings({"unchecked"})
  @Override
  public T fromBytes(byte[] bytes, int offset, int length) {
    try {
      return (T) ((Message)prototype).newBuilderForType().mergeFrom(bytes, offset, length).build();
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }
}
