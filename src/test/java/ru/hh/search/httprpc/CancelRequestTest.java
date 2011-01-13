package ru.hh.search.httprpc;

import com.google.common.util.concurrent.ListenableFuture;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.testng.annotations.Test;
import ru.hh.search.httprpc.netty.NettyClient;
import ru.hh.search.httprpc.netty.NettyServer;
import static org.testng.Assert.assertTrue;

public class CancelRequestTest {
  
  @Test
  public void test() throws ExecutionException, InterruptedException {
    InetSocketAddress address = new InetSocketAddress(12346);
    String basePath = "/apiBase/";
    String path = "longMethod";
    JavaSerializer serializer = new JavaSerializer();
    
    Map<String, Object> serverOptions = new HashMap<String, Object>();
    serverOptions.put("localAddress", address);
    NettyServer server = new NettyServer(serverOptions, serializer, basePath);
    server.register(path, new LongJavaMethod());
    server.startAndWait();

    NettyClient client = new NettyClient(new HashMap<String, Object>(), serializer, basePath);
    ClientMethod<Long, Object> clientMethod = client.createMethod(path, Long.class);

    int sleepTime = 1000;
    ListenableFuture<Long> result = clientMethod.call(address, null, sleepTime);
    result.cancel(true);
    
    assertTrue(result.get() != sleepTime);
  }
}
