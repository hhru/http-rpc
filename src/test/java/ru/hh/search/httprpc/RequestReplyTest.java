package ru.hh.search.httprpc;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import ru.hh.search.httprpc.server.NettyServer;

public class RequestReplyTest {
  
  private NettyServer server;
  
  @BeforeClass
  public void setUp() {
    Map<String, Object> options = new HashMap<String, Object>();
    options.put("localAddress", new InetSocketAddress(12345));
    server = new NettyServer(options);
    server.start();
  }
  
  @Test
  public void test() {
  }
  
  @AfterClass
  public void tearDown() {
    server.stop();
  }
}
