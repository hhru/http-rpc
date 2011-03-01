package ru.hh.httprpc;

import ru.hh.httprpc.serialization.Serializer;

class ServerMethodDescriptor<I, O> {
  final public ServerMethod<I, O> method;
  final public Serializer.ForClass<O> encoder;
  final public Serializer.ForClass<I> decoder;

  ServerMethodDescriptor(ServerMethod<I, O> method, Serializer.ForClass<O> encoder, Serializer.ForClass<I> decoder) {
    this.method = method;
    this.encoder = encoder;
    this.decoder = decoder;
  }
}
