package ru.hh.search.httprpc;

import java.io.InputStream;

public interface Decoder<T>  {
  T fromBytes(byte[] bytes, int offset, int length);
  T fromInputStream(InputStream stream);
}
