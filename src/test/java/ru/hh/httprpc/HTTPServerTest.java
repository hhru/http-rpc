package ru.hh.httprpc;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import ru.hh.httprpc.serialization.JavaSerializer;
import ru.hh.httprpc.util.netty.Http;
import ru.hh.httprpc.util.netty.HttpHandler;

public class HTTPServerTest {

  private final RPC<Long, Long> LONG_METHOD = RPC.signature("/method", Long.class, Long.class);
  private final long RESULT = 1L;
  private final int TEST_WRITE_TIMEOUT = 1000;

  @Test
  public void testUnclosedChannelTimeout() throws Exception {
    final SettableFuture<Channel> channelReference = SettableFuture.create();
    TcpOptions options = TcpOptions.create().localAddress(new InetSocketAddress(InetAddress.getLocalHost(), 0));
    ChannelHandler handler = new HttpHandler() {
      @Override
      protected void requestReceived(final Channel channel, HttpRequest request, Http.UrlDecoder url) throws Exception {
        new Thread() {
          @Override
          public void run() {
            try {
              final JavaSerializer javaSerializer = new JavaSerializer();
              final ChannelBuffer buffer = (ChannelBuffer) javaSerializer.encoder((Class) Long.class).apply(RESULT);
              // processing/sleeping more than timeout, possible in "processing" state. before start sending results
              TimeUnit.MILLISECONDS.sleep(TEST_WRITE_TIMEOUT * 2);
              Http.response(HttpResponseStatus.OK).containing(javaSerializer.getContentType(), buffer).sendTo(channel);
              channelReference.set(channel);
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
        }.start();
      }
    };
    final int serverIoThreads = 1;
    HTTPServer server = HTTPServer.builder()
        .options(options)
        .ioThreads(serverIoThreads)
        .concurrentRequestsLimit(1)
        .writeTimeout(TEST_WRITE_TIMEOUT)
        .handler(handler)
        .build();
    server.startAsync().awaitRunning();
    InetSocketAddress address = server.getLocalAddress();
    final int clientIoThreads = 1;
    RPCClient client = new RPCClient(TcpOptions.create(), "/", clientIoThreads, new JavaSerializer());
    final ClientMethod<Long, Long> method = client.createMethod(LONG_METHOD);
    final ListenableFuture<Long> call = method.call(address, new Envelope(), 0L);

    Channel channel = channelReference.get();

    // result sent, sleeping less than timeout
    TimeUnit.MILLISECONDS.sleep(TEST_WRITE_TIMEOUT / 2);
    assertTrue(channel.isOpen(), "expecting channel to be open");
    // sleeping more than timeout
    TimeUnit.MILLISECONDS.sleep(TEST_WRITE_TIMEOUT * 2);
    assertTrue(!channel.isOpen(), "expecting channel to be closed");

    Long res = call.get();
    assertEquals(res.longValue(), RESULT);
  }
}
