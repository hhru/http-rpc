package ru.hh.search.httprpc.netty;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import org.jboss.netty.buffer.ChannelBufferInputStream;
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
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hh.search.httprpc.Envelope;
import ru.hh.search.httprpc.Http;
import ru.hh.search.httprpc.HttpRpcNames;

class ServerMethodCallHandler extends SimpleChannelUpstreamHandler {
  public static final Logger logger = LoggerFactory.getLogger(ServerMethodCallHandler.class);
  
  private final ChannelGroup allChannels;
  private final ConcurrentMap<String, ServerMethodDescriptor> methods;
  private final ExecutorService methodCallbackExecutor;

  ServerMethodCallHandler(ChannelGroup allChannels, ConcurrentMap<String, ServerMethodDescriptor> methods, ExecutorService methodCallbackExecutor) {
    this.allChannels = allChannels;
    this.methods = methods;
    this.methodCallbackExecutor = methodCallbackExecutor;
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
    // TODO: validate query parameters
    Envelope envelope = new Envelope(Integer.parseInt(uriDecoder.getParameters().get(HttpRpcNames.TIMEOUT).iterator().next()),
      uriDecoder.getParameters().get(HttpRpcNames.REQUEST_ID).iterator().next());
    final String path = uriDecoder.getPath();
    final ServerMethodDescriptor descriptor = methods.get(path);
    if (descriptor == null) {
      Http.response(HttpResponseStatus.NOT_FOUND).
        containing("no method registered on path: " + path).
        sendAndClose(channel);
      return;
    }
    Object argument;
    try {
      argument = Util.decodeContent(descriptor.decoder, request.getContent());
    } catch (Exception decoderException) {
      Http.response(HttpResponseStatus.BAD_REQUEST).
          containing(decoderException).
          sendAndClose(channel);
      return;
    }
    try {
      @SuppressWarnings({"unchecked"}) 
      final ListenableFuture callFuture = descriptor.method.call(envelope, argument);
      Runnable onCallComplete = new Runnable() {
        @Override
        public void run() {
          try {
            try {
              Object result = callFuture.get();
              Http.response(HttpResponseStatus.OK).
                  containing(descriptor.encoder.getContentType(), descriptor.encoder.toBytes(result)).
                  sendAndClose(channel);
            } catch (ExecutionException futureException) {
              logger.error(String.format("method on %s threw an exception", path), futureException.getCause());
              Http.response(HttpResponseStatus.INTERNAL_SERVER_ERROR).
                  containing(futureException.getCause()).
                  sendAndClose(channel);
            }
          } catch (CancellationException e) {
            // nothing to do, channel has been closed already 
          } catch (InterruptedException e) {
            logger.error("got impossible exception, closing channel", e);
            channel.close();
          }
        }
      };
      callFuture.addListener(onCallComplete, methodCallbackExecutor);
      channel.getCloseFuture().addListener(new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
          if (callFuture.cancel(true)) {
            logger.warn("method call on {} cancelled by closed client {} channel", path, channel.getRemoteAddress());
          }
        }
      });
    } catch (Exception callException) {
      Http.response(HttpResponseStatus.INTERNAL_SERVER_ERROR).
          containing(callException).
          sendAndClose(channel);
    }
  }
}
