package ru.hh.search.httprpc.netty;

import ru.hh.search.httprpc.Serializer;
import ru.hh.search.httprpc.ServerMethod;

class ServerMethodDescriptor {
  final public ServerMethod method;
  final public Serializer encoder;
  final public Serializer decoder;

  ServerMethodDescriptor(ServerMethod method, Serializer encoder, Serializer decoder) {
    this.method = method;
    this.encoder = encoder;
    this.decoder = decoder;
  }
}
