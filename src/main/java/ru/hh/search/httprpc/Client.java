package ru.hh.search.httprpc;

public interface Client {
  <O, I> ClientMethod<O, I> createMethod(String path, Encoder<? super I> encoder, Decoder<? extends O> decoder);
}
