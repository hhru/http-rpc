package ru.hh.httprpc;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.channel.ChannelHandler;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutionException;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import org.testng.annotations.Test;
import ru.hh.httprpc.serialization.JavaSerializer;
import ru.hh.httprpc.util.netty.RoutingHandler;

public class RoutingTest {
  @Test
  public void test() throws UnknownHostException, ExecutionException, InterruptedException {
    final JavaSerializer serializer = new JavaSerializer();
    RPCHandler handler1 = new RPCHandler(serializer);
    RPCHandler handler2 = new RPCHandler(serializer);
    RoutingHandler router = new RoutingHandler(
      ImmutableMap.<String, ChannelHandler>builder()
        .put("/one", handler1)
        .put("/two", handler2)
        .build());
    HTTPServer server = new HTTPServer(TcpOptions.create().localAddress(new InetSocketAddress(InetAddress.getLocalHost(), 0)), 2, router);

    RPCClient client1 = new RPCClient(TcpOptions.create(), "/one", 2, serializer);
    RPCClient client2 = new RPCClient(TcpOptions.create(), "/two", 2, serializer);
    RPCClient client3 = new RPCClient(TcpOptions.create(), "/noexisting", 2, serializer);

    RPC<Void, String> signature = RPC.signature("/method", Void.class, String.class);
    server.startAsync().awaitRunning();
    try {
      handler1.register(signature, new ServerMethod<Void, String>() {
        @Override
        public ListenableFuture<String> call(Envelope envelope, Void argument) {
          return Futures.immediateFuture("one");
        }
      });

      handler2.register(signature, new ServerMethod<Void, String>() {
        @Override
        public ListenableFuture<String> call(Envelope envelope, Void argument) {
          return Futures.immediateFuture("two");
        }
      });

      ClientMethod<Void, String> method1 = client1.createMethod(signature);
      ClientMethod<Void, String> method2 = client2.createMethod(signature);
      ClientMethod<Void, String> method3 = client3.createMethod(signature);

      Envelope envelope = new Envelope(1000, "asdfasdfas");
      assertEquals(method1.call(server.getLocalAddress(), envelope, null).get(), "one");
      assertEquals(method2.call(server.getLocalAddress(), envelope, null).get(), "two");

      try {
        assertEquals(method3.call(server.getLocalAddress(), envelope, null).get(), "wtf?");
        fail();
      } catch (ExecutionException e) {
        assertTrue(e.getCause() instanceof BadResponseException);
        assertTrue(e.getCause().getMessage().contains("404"));
      }

    } finally {
      client1.stopAsync().awaitTerminated();
      client2.stopAsync().awaitTerminated();
      client3.stopAsync().awaitTerminated();
      server.stopAsync().awaitTerminated();
    }
  }
}
