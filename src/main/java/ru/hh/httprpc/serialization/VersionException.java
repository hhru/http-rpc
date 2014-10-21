package ru.hh.httprpc.serialization;

import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import ru.hh.httprpc.RPCMethodException;

public class VersionException extends RPCMethodException {
  public final static HttpResponseStatus VERSION_INCOMPATIBLE = new HttpResponseStatus(444, "Version incompatible");

  public VersionException(Throwable cause) {
    super(VERSION_INCOMPATIBLE, cause);
  }
}
