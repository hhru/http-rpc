package ru.hh.search.httprpc.netty;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.jboss.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hh.search.httprpc.Envelope;
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
    QueryStringDecoder uriDecoder = new QueryStringDecoder(request.getUri());
    // TODO: validate query parameters
    Envelope envelope = new Envelope(Integer.parseInt(uriDecoder.getParameters().get(HttpRpcNames.TIMEOUT).iterator().next()),
      uriDecoder.getParameters().get(HttpRpcNames.REQUEST_ID).iterator().next());
    // TODO: no method??
    final String path = uriDecoder.getPath();
    final ServerMethodDescriptor descriptor = methods.get(path);
    Object argument;
    final Channel channel = event.getChannel();
    try {
      // TODO see org.jboss.netty.handler.codec.protobuf.ProtobufDecoder.decode()
      argument = descriptor.decoder.fromInputStream(new ChannelBufferInputStream(request.getContent()));
    } catch (Exception decoderException) {
      channel.write(responseFromException(decoderException, HttpResponseStatus.BAD_REQUEST))
        .addListener(ChannelFutureListener.CLOSE);
      return;
    }
    try {
      @SuppressWarnings({"unchecked"}) 
      final ListenableFuture callFuture = descriptor.method.call(envelope, argument);
      Runnable onCallComplete = new Runnable() {
        @Override
        public void run() {
          try {
            HttpResponse response;
            try {
              Object result = callFuture.get();
              @SuppressWarnings({"unchecked"})
              byte[] bytes = descriptor.encoder.toBytes(result);
              response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
              response.setHeader(HttpHeaders.Names.CONTENT_TYPE, descriptor.encoder.getContentType());
              response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, bytes.length);
              response.setContent(ChannelBuffers.wrappedBuffer(bytes));
            } catch (ExecutionException futureException) {
              logger.error(String.format("method on %s threw an exception", path), futureException.getCause());
              response = responseFromException(futureException.getCause(), HttpResponseStatus.INTERNAL_SERVER_ERROR);
            }
            if (channel.isOpen()) {
              channel.write(response).addListener(ChannelFutureListener.CLOSE);
            } else {
              logger.warn("client on {} had closed connection before method on '{}' finished", channel.getRemoteAddress(), path);
            }
          } catch (CancellationException e) {
            // nothing to do, channel hasbeen closed already 
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
      channel.write(responseFromException(callException, HttpResponseStatus.INTERNAL_SERVER_ERROR))
        .addListener(ChannelFutureListener.CLOSE);
    }
  }

  private HttpResponse responseFromException(Throwable callException, HttpResponseStatus responseStatus) {
    HttpResponse response;
    response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, responseStatus);
    response.setContent(ChannelBuffers.copiedBuffer(Throwables.getStackTraceAsString(callException), CharsetUtil.UTF_8));
    response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8");
    response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, response.getContent().readableBytes());
    return response;
  }

}
