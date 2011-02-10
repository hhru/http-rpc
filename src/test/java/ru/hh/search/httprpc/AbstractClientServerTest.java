package ru.hh.search.httprpc;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import ru.hh.search.httprpc.netty.NettyClient;
import ru.hh.search.httprpc.netty.NettyServer;
import ru.hh.search.httprpc.netty.TcpOptions;

public abstract class AbstractClientServerTest {
  protected InetSocketAddress address;
  protected String basePath = "/apiBase/";
  protected NettyServer server;
  protected NettyClient client;
  protected int ioThreads = 2;
  protected int serverMethodThreads = 8;
  protected ExecutorService serverMethodExecutor;

  protected SerializerFactory serializerFactory() {
    return new JavaSerializerFactory();
  }

  @BeforeMethod
  public void start() throws UnknownHostException {
    TcpOptions serverOptions = TcpOptions.create().setLocalAddress(new InetSocketAddress(InetAddress.getLocalHost(), 0));
    serverMethodExecutor = Executors.newFixedThreadPool(serverMethodThreads);
    server = new NettyServer(serverOptions, basePath, ioThreads, serializerFactory());
    server.startAndWait();
    address = server.getLocalAddress();
    client = new NettyClient(TcpOptions.create(), basePath, ioThreads, serializerFactory());
  }

  @AfterMethod
  public void stop() {
    serverMethodExecutor.shutdownNow();
    client.stopAndWait();
    server.stopAndWait();
  }
}
