package ru.hh.httprpc;

import com.google.common.collect.ImmutableMap;
import org.jboss.netty.channel.ChannelHandler;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import ru.hh.httprpc.serialization.JavaSerializer;
import ru.hh.httprpc.util.netty.RoutingHandler;
import ru.hh.httprpc.InetSocketAddress;

import java.net.InetAddress;
import java.net.UnknownHostException;

public abstract class AbstractClientServerTest {
  protected static final RPC<Long, Long> LONG2LONG_METHOD = RPC.signature("/method", Long.class, Long.class);

  protected InetSocketAddress address;
  protected String basePath = "/apiBase";
  protected HTTPServer server;
  protected RPCHandler serverHandler;
  protected RPCClient client;
  protected int ioThreads = 2;

  @BeforeMethod
  public void start() throws UnknownHostException {
    TcpOptions serverOptions = TcpOptions.create().localAddress(new InetSocketAddress(InetAddress.getLocalHost(), 0));
    serverHandler = new RPCHandler(new JavaSerializer());
    RoutingHandler router = new RoutingHandler(
      ImmutableMap.<String, ChannelHandler>builder().put(basePath, serverHandler).build());
    server = new HTTPServer(serverOptions, ioThreads, router);
    server.startAndWait();
    address = server.getLocalAddress();
    client = new RPCClient(TcpOptions.create(), basePath, ioThreads, new JavaSerializer());
  }

  @AfterMethod
  public void stop() {
    client.stopAndWait();
    server.stopAndWait();
  }
}
