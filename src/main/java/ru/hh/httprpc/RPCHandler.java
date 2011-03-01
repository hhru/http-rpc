package ru.hh.httprpc;

import com.google.common.base.Preconditions;
import com.google.common.collect.MapMaker;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static ru.hh.httprpc.HttpRpcNames.REQUEST_ID;
import static ru.hh.httprpc.HttpRpcNames.TIMEOUT;
import ru.hh.httprpc.serialization.SerializationException;
import ru.hh.httprpc.serialization.Serializer;
import ru.hh.httprpc.util.concurrent.FutureListener;
import ru.hh.httprpc.util.netty.Http;

public class RPCHandler extends SimpleChannelUpstreamHandler {
  public static final Logger logger = LoggerFactory.getLogger(RPCHandler.class);

  private final Serializer serializer;
  private final ConcurrentMap<String, ServerMethodDescriptor<? super Object, ? super Object>> methods;

  RPCHandler(Serializer serializer) {
    this.serializer = serializer;
    this.methods = new MapMaker().makeMap();
  }

  @SuppressWarnings({"unchecked"})
  public <I, O> void register(RPC<I, O> signature, ServerMethod<I, O> method) {
    methods.put(signature.path,
                new ServerMethodDescriptor(method, serializer.forClass(signature.outputClass),
                                           serializer.forClass(signature.inputClass)));
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
    logger.error("server got an exception, closing channel", e.getCause());
    e.getChannel().close();
  }
  
  @Override
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent event) throws Exception {
    HttpRequest request = (HttpRequest) event.getMessage();
    final Channel channel = event.getChannel();
    QueryStringDecoder uriDecoder = new QueryStringDecoder(request.getUri());
    Envelope envelope;
    try {
      Map<String,List<String>> parameters = uriDecoder.getParameters();
      List<String> rawTimeout = parameters.get(TIMEOUT);
      Preconditions.checkArgument(rawTimeout.size() == 1, "single " + TIMEOUT + " parameter required");
      List<String> rawRequestId = parameters.get(REQUEST_ID);
      Preconditions.checkArgument(rawRequestId.size() == 1, "single " + REQUEST_ID + " parameter required");
      envelope = new Envelope(Integer.parseInt(rawTimeout.get(0)), rawRequestId.get(0));
    } catch (Exception parametersException) {
      Http.response(BAD_REQUEST).
          containing(parametersException).
          sendAndClose(channel);
      return;
    }
    final String path = uriDecoder.getPath();
    final ServerMethodDescriptor<? super Object, ? super Object> descriptor = methods.get(path);
    if (descriptor == null) {
      Http.response(NOT_FOUND).
        containing("no method registered on path: " + path).
        sendAndClose(channel);
      return;
    }
    try {
      final ListenableFuture callFuture = descriptor.call(envelope, request.getContent());
      new FutureListener<Object>(callFuture) {
        protected void success(Object result) {
          try {
            Http.response(OK).
                containing(descriptor.encoder.getContentType(), descriptor.encoder.serialize(result)).
                sendAndClose(channel);
          } catch (SerializationException e) {
            Http.response(INTERNAL_SERVER_ERROR).
                containing(e).
                sendAndClose(channel);
          }
        }

        protected void exception(Throwable exception) {
          logger.error(String.format("method on %s threw an exception", path), exception);
          Http.response(INTERNAL_SERVER_ERROR).
              containing(exception).
              sendAndClose(channel);
        }
      };
      channel.getCloseFuture().addListener(new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
          if (callFuture.cancel(true)) {
            logger.warn("method call on {} cancelled by closed client {} channel", path, channel.getRemoteAddress());
          }
        }
      });
    } catch (SerializationException decoderException) {
      Http.response(BAD_REQUEST).
          containing(decoderException).
          sendAndClose(channel);
    } catch (Exception callException) {
      Http.response(INTERNAL_SERVER_ERROR).
          containing(callException).
          sendAndClose(channel);
    }
  }

  private static class ServerMethodDescriptor<I, O> {
    final public ServerMethod<I, O> method;
    final public Serializer.ForClass<O> encoder;
    final public Serializer.ForClass<I> decoder;

    public ServerMethodDescriptor(ServerMethod<I, O> method, Serializer.ForClass<O> encoder, Serializer.ForClass<I> decoder) {
      this.method = method;
      this.encoder = encoder;
      this.decoder = decoder;
    }

    public ListenableFuture<O> call(Envelope envelope, ChannelBuffer input) throws SerializationException {
      return method.call(envelope, decoder.deserialize(input));
    }
  }
}
