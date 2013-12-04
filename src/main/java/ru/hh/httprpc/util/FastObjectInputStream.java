package ru.hh.httprpc.util;

import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.util.concurrent.ExecutionException;

public class FastObjectInputStream extends ObjectInputStream {
  private static final LoadingCache<String, Class<?>> CLASS_CACHE = CacheBuilder.newBuilder().
      concurrencyLevel(Runtime.getRuntime().availableProcessors()).
      build(new CacheLoader<String, Class<?>>() {
        @Override
        public Class<?> load(String input) throws Exception {
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
    try {
      return CLASS_CACHE.get(desc.getName());
    } catch (ExecutionException e) {
      throw Throwables.propagate(e);
    }
  }
}
