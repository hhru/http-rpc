package ru.hh.search.httprpc;

import com.google.common.util.concurrent.ListenableFuture;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import org.testng.annotations.Test;
import ru.hh.search.httprpc.netty.NettyClient;
import static org.testng.Assert.fail;

public class ConnectionFailTest {

  @Test(expectedExceptions = ExecutionException.class)
  public void noServer() throws ExecutionException, InterruptedException {
    InetSocketAddress address = new InetSocketAddress(12346);
    NettyClient client = new NettyClient(new HashMap<String, Object>(), "base");
    JavaSerializer serializer = new JavaSerializer();

    try {
      ClientMethod method = client.createMethod("abracadabra", serializer, serializer);
      ListenableFuture future = method.call(address, new Envelope(10, "asdfa"), "hello");
      future.get();
      fail();
    } finally {
      client.stopAndWait();
    }
  }
}
