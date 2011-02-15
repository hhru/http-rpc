package ru.hh.search.httprpc.util;

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.common.collect.MapMaker;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.util.concurrent.ConcurrentMap;

public class FastObjectInputStream extends ObjectInputStream {
  private static final ConcurrentMap<String, Class<?>> CLASS_CACHE = new MapMaker().
      concurrencyLevel(Runtime.getRuntime().availableProcessors()).
      makeComputingMap(new Function<String, Class<?>>() {
        public Class<?> apply(String input) {
          try {
            return Class.forName(input);
          } catch (ClassNotFoundException e) {
            throw Throwables.propagate(e);
          }
        }
      });

  public FastObjectInputStream(InputStream in) throws IOException {
    super(in);
  }

  protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
    return CLASS_CACHE.get(desc.getName());
  }
}
