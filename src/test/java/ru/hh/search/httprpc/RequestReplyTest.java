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
      {new JavaSerializer(), new JavaSerializer(), new JavaMethod(), "hello"},
      {new ProtobufSerializer(Messages.Request.getDefaultInstance()), 
        new ProtobufSerializer(Messages.Reply.getDefaultInstance()), 
        new ProtobufMethod(), 
        Messages.Request.newBuilder().setRequest("hello").build()}
    };
  }
  
  @Test(dataProvider = "methods")
  public void test(Serializer inputSerializer, Serializer outputSerializer, ServerMethod serverMethod, Object argument) throws ExecutionException, InterruptedException {
    InetSocketAddress address = new InetSocketAddress(12345);
    String basePath = null;
    String path = "/helloMethod";
    
    Map<String, Object> serverOptions = new HashMap<String, Object>();
    serverOptions.put("localAddress", address);
    NettyServer server = new NettyServer(serverOptions, basePath);
    server.register(path, serverMethod, outputSerializer, inputSerializer);
    server.startAndWait();

    Object local = serverMethod.call(null, argument);
    
    NettyClient client = new NettyClient(new HashMap<String, Object>(), basePath);
    ClientMethod clientMethod = client.createMethod(path, inputSerializer, outputSerializer);
    Object remote = clientMethod.call(address, null, argument).get();
    
    assertEquals(remote, local);

    client.stopAndWait();
    server.stopAndWait();
  }
}
