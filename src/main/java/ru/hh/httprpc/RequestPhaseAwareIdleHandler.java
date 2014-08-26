package ru.hh.httprpc;

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.DownstreamMessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.UpstreamMessageEvent;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import org.jboss.netty.handler.timeout.IdleState;
import org.jboss.netty.handler.timeout.IdleStateAwareChannelHandler;
import org.jboss.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hh.httprpc.util.netty.Http;

class RequestPhaseAwareIdleHandler extends IdleStateAwareChannelHandler {
  private static final Logger logger = LoggerFactory.getLogger(RequestPhaseAwareIdleHandler.class);
  enum RequestPhase {
    READING,
    PROCESSING,
    WRITING
  }

  private volatile RequestPhase phase = RequestPhase.READING;
  @Override
   public void channelIdle(ChannelHandlerContext ctx, IdleStateEvent e) {
     if (phase == RequestPhase.READING && e.getState() == IdleState.READER_IDLE) {
       logger.warn("read timeout, closing channel");
       Http.response(INTERNAL_SERVER_ERROR).
           containing("read timeout").
           sendAndClose(e.getChannel());
     } else if (phase == RequestPhase.WRITING && e.getState() == IdleState.WRITER_IDLE) {
       logger.warn("write timeout, closing channel");
       e.getChannel().close();
     }
  }

  public SimpleChannelUpstreamHandler startProcessing() {
    return new SimpleChannelUpstreamHandler() {
      @Override
      public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        if (phase == RequestPhase.READING && e instanceof UpstreamMessageEvent) {
          logger.debug("start processing response");
          RequestPhaseAwareIdleHandler.this.phase = RequestPhase.PROCESSING;
        }
        super.handleUpstream(ctx, e);
      }
    };
  }

  public SimpleChannelDownstreamHandler startWriting() {
    return new SimpleChannelDownstreamHandler() {
      @Override
      public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        if (phase == RequestPhase.PROCESSING && e instanceof DownstreamMessageEvent) {
          logger.debug("start writing response");
          RequestPhaseAwareIdleHandler.this.phase = RequestPhase.WRITING;
        }
        super.handleDownstream(ctx, e);
      }
    };
  }
}
