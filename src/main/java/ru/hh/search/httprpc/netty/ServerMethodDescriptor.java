package ru.hh.search.httprpc.netty;

import ru.hh.search.httprpc.Serializer;
import ru.hh.search.httprpc.ServerMethod;

class ServerMethodDescriptor<I, O> {
  final public ServerMethod<I, O> method;
  final public Serializer<O> encoder;
  final public Serializer<I> decoder;

  ServerMethodDescriptor(ServerMethod<I, O> method, Serializer<O> encoder, Serializer<I> decoder) {
    this.method = method;
    this.encoder = encoder;
    this.decoder = decoder;
  }
}
