package ru.hh.httprpc;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.MapMaker;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static java.lang.String.format;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static ru.hh.httprpc.HttpRpcNames.REQUEST_ID;
import static ru.hh.httprpc.HttpRpcNames.TIMEOUT;
import ru.hh.httprpc.serialization.Serializer;
import ru.hh.httprpc.util.concurrent.FutureListener;
import ru.hh.httprpc.util.netty.Http;
import ru.hh.httprpc.util.netty.HttpHandler;

@Sharable
public class RPCHandler extends HttpHandler {
  public static final Logger logger = LoggerFactory.getLogger(RPCHandler.class);

  private final Serializer serializer;
  private final ConcurrentMap<String, ServerMethodDescriptor<?, ?>> methods;
  private volatile boolean prohibitCancellation = false;

  public RPCHandler(Serializer serializer) {
    this.serializer = serializer;
    this.methods = new MapMaker().makeMap();
  }

  public <I, O> void register(RPC<I, O> signature, ServerMethod<I, O> method) {
    methods.put(
        signature.path,
        new ServerMethodDescriptor<I, O>(method, serializer.decoder(signature.inputClass), serializer.encoder(signature.outputClass))
    );
  }

  @Override
  protected void requestReceived(final Channel channel, FullHttpRequest request, Http.UrlDecoder url) throws Exception {
    final String path = url.path();
    try {
      final ServerMethodDescriptor<?, ?> descriptor = methods.get(path);
      if (descriptor == null) {
        logger.error(format("method '%s' not found", path));
        Http.response(NOT_FOUND).
            containing(format(
                "method '%s' not found.\n" +
                    "available methods:\n" +
                    "%s\n",
                path,
                methods.isEmpty() ? "<none>" : Joiner.on("\n").join(methods.keySet())
            )).sendAndClose(channel);
        return;
      }

      final ListenableFuture<ByteBuf> methodFuture = descriptor.call(
          new Envelope(
              url.optionalSingleLong(TIMEOUT, Envelope.DEFAULT_TIMEOUT),
              url.optionalSingleString(REQUEST_ID, Envelope.DEFAULT_REQUESTID)
          ),
          request.content()
      );

      new FutureListener<ByteBuf>(methodFuture) {
        protected void success(ByteBuf result) {
          Http.response(OK).
              containing(serializer.getContentType(), result).
              sendAndClose(channel);
        }

        protected void exception(Throwable e) {
          exceptionHandler(e, channel, path);
        }
      };

      if (!prohibitCancellation) {
        channel.closeFuture().addListener(new ChannelFutureListener() {
          public void operationComplete(ChannelFuture future) throws Exception {
            if (methodFuture.cancel(true))
              logger.debug("'{}' method call was cancelled by client ({})", path, channel.remoteAddress());
          }
        });
      }
    } catch (Exception e) {
      exceptionHandler(e, channel, path);
    }
  }

  private void exceptionHandler(Throwable e, Channel channel, String path) {
    if (e instanceof RPCMethodException) {
      RPCMethodException methodException = (RPCMethodException) e;
      sendError(channel, methodException.getStatus(), e, e.getMessage(), path);
    } else if (e instanceof IllegalArgumentException) {
      sendError(channel, BAD_REQUEST, e, "bad parameters for '%s'", path);
    } else {
      sendError(channel, INTERNAL_SERVER_ERROR, e, "'%s' method future failed", path);
    }
  }

  private static void sendError(Channel channel, HttpResponseStatus status, Throwable exception, String message, Object... args) {
    logger.error(format(message, args), exception);
    Http.response(status).
        containing(exception).
        sendAndClose(channel);
  }

  private static class ServerMethodDescriptor<I, O> {
    final public ServerMethod<I, O> method;
    Function<ByteBuf, I> decoder;
    Function<O, ByteBuf> encoder;

    public ServerMethodDescriptor(ServerMethod<I, O> method, Function<ByteBuf, I> decoder, Function<O, ByteBuf> encoder) {
      this.method = method;
      this.decoder = decoder;
      this.encoder = encoder;
    }

    public ListenableFuture<ByteBuf> call(Envelope envelope, ByteBuf input) {
      return Futures.transform(method.call(envelope, decoder.apply(input)), encoder);
    }
  }

  public void setProhibitCancellation(boolean prohibitCancellation) {
    this.prohibitCancellation = prohibitCancellation;
  }
}
