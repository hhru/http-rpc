package ru.hh.httprpc;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.jboss.netty.channel.ChannelHandler;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import ru.hh.httprpc.serialization.ProtobufSerializer;
import ru.hh.httprpc.util.netty.RoutingHandler;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutionException;

import static org.testng.Assert.assertEquals;

public class ProtobufTest {
  protected String basePath = "/apiBase";
  protected HTTPServer server;
  protected RPCHandler serverHandler;
  protected RPCClient client;

  @BeforeMethod
  public void start() throws UnknownHostException {
    TcpOptions serverOptions = TcpOptions.create().localAddress(new InetSocketAddress(InetAddress.getLocalHost(), 0));
    serverHandler = new RPCHandler(new ProtobufSerializer());
    RoutingHandler router = new RoutingHandler(
      ImmutableMap.<String, ChannelHandler>builder().put(basePath, serverHandler).build());
    server = new HTTPServer(serverOptions, 2, router);
    server.startAndWait();
    client = new RPCClient(TcpOptions.create(), basePath, 2, new ProtobufSerializer());
  }

  @AfterMethod
  public void stop() {
    client.stopAndWait();
    server.stopAndWait();
  }

  @Test
  public void test() throws ExecutionException, InterruptedException {
    RPC<Messages.Request, Messages.Reply> signature = RPC.signature("/helloMethod", Messages.Request.class, Messages.Reply.class);
    ProtobufMethod serverMethod = new ProtobufMethod();
    serverHandler.register(signature, serverMethod);

    final Messages.Request argument = Messages.Request.newBuilder().setRequest("hello").build();
    
    Messages.Reply local = serverMethod.call(null, argument).get();
    
    ClientMethod<Messages.Request, Messages.Reply> clientMethod = client.createMethod(signature);
    Messages.Reply remote = clientMethod.call(server.getLocalAddress(), new Envelope(10, "asdf"), argument).get();
    
    assertEquals(remote, local);
  }

  public static class ProtobufMethod implements ServerMethod<Messages.Request, Messages.Reply> {
    @Override
    public ListenableFuture<Messages.Reply> call(Envelope envelope, Messages.Request argument) {
      return Futures.immediateFuture(Messages.Reply.newBuilder().setReply(argument.getRequest().toUpperCase()).build());
    }
  }
}
