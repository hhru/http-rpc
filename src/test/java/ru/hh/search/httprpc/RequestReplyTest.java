package ru.hh.search.httprpc;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import ru.hh.search.httprpc.netty.NettyClient;
import ru.hh.search.httprpc.netty.NettyServer;
import static org.testng.Assert.assertEquals;

public class RequestReplyTest {

  @DataProvider(name = "methods")
  public Object[][] methods() {
    return new Object[][] {
      {new JavaSerializer(), new JavaMethod(), "hello"},
      {new ProtobufSerializer(), new ProtobufMethod(), Messages.Request.newBuilder().setRequest("hello").build()}
    };
  }
  
  @Test(dataProvider = "methods")
  public void test(Serializer serializer, ServerMethod method, Object argument) throws ExecutionException, InterruptedException {
    Map<String, Object> serverOptions = new HashMap<String, Object>();
    serverOptions.put("localAddress", new InetSocketAddress(12345));
    NettyServer server = new NettyServer(serverOptions, serializer);
    server.register(method);
    server.startAndWait();

    Map<String, Object> clientOptions = new HashMap<String, Object>();
    clientOptions.put("remoteAddress", new InetSocketAddress(12345));
    NettyClient client = new NettyClient(clientOptions, serializer);
    client.startAndWait();

    Object local = method.call(null, argument);
    Object remote = client.call(method.getPath(), null, argument, local.getClass()).get();
    assertEquals(remote, local);

    client.stopAndWait();
    server.stopAndWait();
  }
}
