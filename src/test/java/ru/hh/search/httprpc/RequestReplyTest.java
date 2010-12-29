package ru.hh.search.httprpc;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import ru.hh.search.httprpc.client.NettyClient;
import ru.hh.search.httprpc.server.NettyServer;

public class RequestReplyTest {
  private NettyServer server;
  private NettyClient client;
  
  @BeforeClass
  public void setUp() {
    Map<String, Object> serverOptions = new HashMap<String, Object>();
    serverOptions.put("localAddress", new InetSocketAddress(12345));
    server = new NettyServer(serverOptions);
    server.startAndWait();
    
    Map<String, Object> clientOptions = new HashMap<String, Object>();
    clientOptions.put("remoteAddress", new InetSocketAddress(12345));
    client = new NettyClient(clientOptions);
    client.startAndWait();
  }
  
  @Test
  public void test() {
  }
  
  @AfterClass
  public void tearDown() {
    client.stopAndWait();
    server.stopAndWait();
  }
}
