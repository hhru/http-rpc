package ru.hh.search.httprpc;

import java.util.Map;

public class ProtobufMethod implements ServerMethod<Messages.Reply, Messages.Request> {
  @Override
  public String getPath() {
    return "/protobufMethod";
  }

  @Override
  public Class<Messages.Request> getInputClass() {
    return Messages.Request.class;
  }

  @Override
  public Messages.Reply call(Map<String, String> envelope, Messages.Request argument) {
    return Messages.Reply.newBuilder().setReply(argument.getRequest().toUpperCase()).build();
  }
}
