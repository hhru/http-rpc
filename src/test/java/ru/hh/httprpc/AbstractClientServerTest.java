package ru.hh.httprpc;

import com.google.inject.internal.ImmutableMap;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jboss.netty.channel.ChannelHandler;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import ru.hh.httprpc.serialization.JavaSerializer;
import ru.hh.httprpc.serialization.Serializer;
import ru.hh.httprpc.util.netty.RoutingChannelHandler;

public abstract class AbstractClientServerTest {
  protected InetSocketAddress address;
  protected String basePath = "/apiBase/";
  protected HTTPServer server;
  protected RPCHandler serverHandler;
  protected RPCClient client;
  protected int ioThreads = 2;
  protected int serverMethodThreads = 8;
  protected ExecutorService serverMethodExecutor;

  protected Serializer serializer() {
    return new JavaSerializer();
  }

  @BeforeMethod
  public void start() throws UnknownHostException {
    TcpOptions serverOptions = TcpOptions.create().localAddress(new InetSocketAddress(InetAddress.getLocalHost(), 0));
    serverMethodExecutor = Executors.newFixedThreadPool(serverMethodThreads);
    serverHandler = new RPCHandler(serializer());
    RoutingChannelHandler router = new RoutingChannelHandler(
      ImmutableMap.<String, ChannelHandler>builder().put(basePath, serverHandler).build());
    server = new HTTPServer(serverOptions, ioThreads, router);
    server.startAndWait();
    address = server.getLocalAddress();
    client = new RPCClient(TcpOptions.create(), basePath, ioThreads, serializer());
  }

  @AfterMethod
  public void stop() {
    serverMethodExecutor.shutdownNow();
    client.stopAndWait();
    server.stopAndWait();
  }
}
