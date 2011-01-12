package ru.hh.search.httprpc;

public interface Client {
  <O, I> ClientMethod<O, I> createMethod(String path, Class<O> outputClass);
}
