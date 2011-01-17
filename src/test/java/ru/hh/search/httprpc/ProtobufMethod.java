package ru.hh.search.httprpc;

public class ProtobufMethod implements ServerMethod<Messages.Reply, Messages.Request> {
  @Override
  public Messages.Reply call(Envelope envelope, Messages.Request argument) {
    return Messages.Reply.newBuilder().setReply(argument.getRequest().toUpperCase()).build();
  }
}
