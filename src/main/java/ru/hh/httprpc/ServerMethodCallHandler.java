package ru.hh.httprpc;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
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
import ru.hh.httprpc.util.concurrent.FutureListener;
import ru.hh.httprpc.util.netty.Http;

class ServerMethodCallHandler extends SimpleChannelUpstreamHandler {
  public static final Logger logger = LoggerFactory.getLogger(ServerMethodCallHandler.class);
  
  private final ChannelGroup allChannels;
  private final ConcurrentMap<String, ServerMethodDescriptor<? super Object, ? super Object>> methods;

  ServerMethodCallHandler(ChannelGroup allChannels, ConcurrentMap<String, ServerMethodDescriptor<? super Object, ? super Object>> methods) {
    this.allChannels = allChannels;
    this.methods = methods;
  }

  @Override
  public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
    allChannels.add(e.getChannel());
    ctx.sendUpstream(e);
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
      long timeout;
      if (rawTimeout == null)
        timeout = Envelope.DEFAULT_TIMEOUT;
      else if (rawTimeout.size() == 1)
        timeout = Integer.parseInt(rawTimeout.get(0));
      else
        throw new IllegalArgumentException("more than 1 " + TIMEOUT + " parameter");
      
      List<String> rawRequestId = parameters.get(REQUEST_ID);
      String requestId;
      if (rawRequestId == null)
        requestId = Envelope.DEFAULT_REQUESTID;
      else if (rawRequestId.size() == 1) 
        requestId = rawRequestId.get(0);
      else
        throw new IllegalArgumentException("more than 1 " + REQUEST_ID + " parameter");
      
      envelope = new Envelope(timeout, requestId);
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
      final ListenableFuture callFuture = descriptor.method.call(envelope, descriptor.decoder.deserialize(request.getContent()));
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
}
