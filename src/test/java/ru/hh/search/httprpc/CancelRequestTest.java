package ru.hh.search.httprpc;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ListenableFuture;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import ru.hh.search.httprpc.netty.NettyClient;
import ru.hh.search.httprpc.netty.NettyServer;
import static org.testng.Assert.fail;

public class CancelRequestTest {
  @DataProvider(name = "times")
  public Object[][] methods() {
    return new Object[][] {
      {0, 1000},
      {1, 1000},
      {100, 1000}
    };
  }
  
  @Test(expectedExceptions = CancellationException.class, dataProvider = "times") 
  public void test(long clientTime, long serverTime) throws ExecutionException, InterruptedException {
    InetSocketAddress address = new InetSocketAddress(12346);
    String basePath = "/apiBase/";
    String path = "longMethod";
    JavaSerializer serializer = new JavaSerializer();
    
    Map<String, Object> serverOptions = new HashMap<String, Object>();
    serverOptions.put("localAddress", address);
    NettyServer server = new NettyServer(serverOptions, basePath);
    server.register(path, new LongJavaMethod(), serializer, serializer);
    server.startAndWait();

    NettyClient client = new NettyClient(new HashMap<String, Object>(), basePath);
    ClientMethod<Long, Object> clientMethod = client.createMethod(path, serializer, serializer);

    try {
      ListenableFuture<Long> result = clientMethod.call(address, null, serverTime);
      if (clientTime > 0) {
        Thread.sleep(clientTime);
      }
      result.cancel(true);

      result.get();
      fail();
    } finally {
      client.stopAndWait();
      server.stopAndWait();
    }
  }

  public static class LongJavaMethod implements ServerMethod<Long, Long> {
    public static final Logger logger = LoggerFactory.getLogger(LongJavaMethod.class); 
    
    @Override
    public Long call(Envelope envelope, Long argument) {
      try {
        Thread.sleep(argument);
        return argument;
      } catch (InterruptedException e) {
        throw Throwables.propagate(e);
      }
    }
  }
}
