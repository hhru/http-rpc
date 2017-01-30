package ru.hh.httprpc.serialization;

import com.google.common.base.Function;

import java.io.InputStream;

public interface BaseSerializer<I, O> {
  String getContentType();

  Function<O, byte[]> encoder(Class<O> clazz);
  Function<InputStream, I> decoder(Class<I> clazz);
}
