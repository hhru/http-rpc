package ru.hh.httprpc.serialization;

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import ru.hh.httprpc.RPCMethodException;

public class SerializationException extends RPCMethodException {
  public SerializationException(Throwable cause) {
    super(BAD_REQUEST, cause);
  }
}
