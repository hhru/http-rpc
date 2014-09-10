package ru.hh.httprpc;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hh.httprpc.util.netty.Http;

@ChannelHandler.Sharable
class RequestPhaseAwareIdleHandler extends ChannelDuplexHandler {
  private static final Logger logger = LoggerFactory.getLogger(RequestPhaseAwareIdleHandler.class);
  enum RequestPhase {
    READING,
    PROCESSING,
    WRITING
  }

  private volatile RequestPhase phase = RequestPhase.READING;
  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    if (evt instanceof IdleStateEvent) {
      IdleStateEvent e = (IdleStateEvent) evt;
      if (phase == RequestPhase.READING && e.state() == IdleState.READER_IDLE) {
        logger.warn("read timeout, closing channel");
        Http.response(HttpResponseStatus.INTERNAL_SERVER_ERROR).
            containing("read timeout").
            sendAndClose(ctx.channel());
      } else if (phase == RequestPhase.WRITING && e.state() == IdleState.WRITER_IDLE) {
        logger.warn("write timeout, closing channel");
        ctx.channel().close();
      }
    }
  }

  public ChannelInboundHandlerAdapter startProcessing() {
    return new ChannelInboundHandlerAdapter() {
      @Override
      public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (phase == RequestPhase.READING) {
          logger.debug("start processing response");
          RequestPhaseAwareIdleHandler.this.phase = RequestPhase.PROCESSING;
        }
        super.channelRead(ctx, msg);
      }
    };
  }

  public ChannelOutboundHandlerAdapter startWriting() {
    return new ChannelOutboundHandlerAdapter() {
      @Override
      public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (phase == RequestPhase.PROCESSING) {
          logger.debug("start writing response");
          RequestPhaseAwareIdleHandler.this.phase = RequestPhase.WRITING;
        }
        super.write(ctx, msg, promise);
      }
    };
  }
}
