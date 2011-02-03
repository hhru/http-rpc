package ru.hh.search.httprpc;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import ru.hh.search.httprpc.netty.NettyClient;
import ru.hh.search.httprpc.netty.NettyServer;

public class AbstractClientServerTest {
  protected InetSocketAddress address = new InetSocketAddress(12346);
  protected String basePath = "/apiBase/";
  protected NettyServer server;
  protected NettyClient client;
  protected int ioThreads = 2;
  protected int callThreads = 8;

  @BeforeMethod
  public void start() {
    Map<String, Object> serverOptions = new HashMap<String, Object>();
    serverOptions.put("localAddress", address);
    server = new NettyServer(serverOptions, basePath, ioThreads, Executors.newFixedThreadPool(callThreads));
    server.startAndWait();
    client = new NettyClient(new HashMap<String, Object>(), basePath, ioThreads);
  }

  @AfterMethod
  public void stop() {
    client.stopAndWait();
    server.stopAndWait();
  }
}
