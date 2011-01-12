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
  public void test(Serializer serializer, ServerMethod serverMethod, Object argument) throws ExecutionException, InterruptedException {
    InetSocketAddress address = new InetSocketAddress(12345);
    String path = "/helloMethod";
    
    Map<String, Object> serverOptions = new HashMap<String, Object>();
    serverOptions.put("localAddress", address);
    NettyServer server = new NettyServer(serverOptions, serializer);
    server.register(path, serverMethod);
    server.startAndWait();

    NettyClient client = new NettyClient(new HashMap<String, Object>(), serializer);
    client.startAndWait(); // TODO move to constructor
    
    Object local = serverMethod.call(null, argument);
    
    ClientMethod clientMethod = client.createMethod(path, local.getClass());
    Object remote = clientMethod.call(address, null, argument).get();
    
    assertEquals(remote, local);

    client.stopAndWait();
    server.stopAndWait();
  }
}
