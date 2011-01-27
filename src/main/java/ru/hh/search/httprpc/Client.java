package ru.hh.search.httprpc;

public interface Client {
  <O, I> ClientMethod<O, I> createMethod(String path, Serializer<? super I> encoder, Serializer<? extends O> decoder);
}
