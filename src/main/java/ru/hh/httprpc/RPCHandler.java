package ru.hh.httprpc;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.MapMaker;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hh.httprpc.serialization.SerializationException;
import ru.hh.httprpc.serialization.Serializer;
import ru.hh.httprpc.util.concurrent.FutureListener;
import ru.hh.httprpc.util.netty.Http;
import ru.hh.httprpc.util.netty.HttpHandler;

import java.util.concurrent.ConcurrentMap;

import static java.lang.String.format;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.*;
import static ru.hh.httprpc.HttpRpcNames.REQUEST_ID;
import static ru.hh.httprpc.HttpRpcNames.TIMEOUT;

@Sharable
public class RPCHandler extends HttpHandler {
  public static final Logger logger = LoggerFactory.getLogger(RPCHandler.class);

  private final Serializer serializer;
  private final ConcurrentMap<String, ServerMethodDescriptor<?, ?>> methods;

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

  protected void requestReceived(final Channel channel, HttpRequest request, Http.UrlDecoder url) throws Exception {
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

      final ListenableFuture<ChannelBuffer> methodFuture = descriptor.call(
          new Envelope(
              url.optionalSingleLong(TIMEOUT, Envelope.DEFAULT_TIMEOUT),
              url.optionalSingleString(REQUEST_ID, Envelope.DEFAULT_REQUESTID)
          ),
          request.getContent()
      );

      new FutureListener<ChannelBuffer>(methodFuture) {
        protected void success(ChannelBuffer result) {
          Http.response(OK).
              containing(serializer.getContentType(), result).
              sendAndClose(channel);
        }

        protected void exception(Throwable e) {
          exceptionHandler(e, channel, path);
        }
      };

      channel.getCloseFuture().addListener(new ChannelFutureListener() {
        public void operationComplete(ChannelFuture future) throws Exception {
          if (methodFuture.cancel(true))
            logger.debug("'{}' method call was cancelled by client ({})", path, channel.getRemoteAddress());
        }
      });
    } catch (Exception e) {
      exceptionHandler(e, channel, path);
    }
  }

  private void exceptionHandler(Throwable e, Channel channel, String path) {
    if (e instanceof IllegalArgumentException) {
      sendError(channel, BAD_REQUEST, e, "bad parameters for '%s'", path);
    } else if (e instanceof SerializationException) {
      sendError(channel, BAD_REQUEST, e, "bad request body for '%s'", path);
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
    Function<ChannelBuffer, I> decoder;
    Function<O, ChannelBuffer> encoder;

    public ServerMethodDescriptor(ServerMethod<I, O> method, Function<ChannelBuffer, I> decoder, Function<O, ChannelBuffer> encoder) {
      this.method = method;
      this.decoder = decoder;
      this.encoder = encoder;
    }

    public ListenableFuture<ChannelBuffer> call(Envelope envelope, ChannelBuffer input) {
      return Futures.transform(method.call(envelope, decoder.apply(input)), encoder);
    }
  }
}
