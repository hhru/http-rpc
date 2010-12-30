package ru.hh.search.httprpc;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import ru.hh.search.httprpc.client.NettyClient;
import ru.hh.search.httprpc.server.NettyServer;
import static org.testng.Assert.assertEquals;

public class RequestReplyTest {
  private NettyServer server;
  private NettyClient client;
  private HelloMethod method;

  @BeforeClass
  public void setUp() {
    Map<String, Object> serverOptions = new HashMap<String, Object>();
    serverOptions.put("localAddress", new InetSocketAddress(12345));
    server = new NettyServer(serverOptions);
    method = new HelloMethod();
    server.register(method);
    server.startAndWait();
    
    Map<String, Object> clientOptions = new HashMap<String, Object>();
    clientOptions.put("remoteAddress", new InetSocketAddress(12345));
    client = new NettyClient(clientOptions);
    client.startAndWait();
  }
  
  @Test
  public void test() throws ExecutionException, InterruptedException {
    String argument = "hello!";
    assertEquals(client.call(method.getUri(), null, argument).get(), method.call(null, argument));
  }
  
  @AfterClass
  public void tearDown() {
    client.stopAndWait();
    server.stopAndWait();
  }
}
