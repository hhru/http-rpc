package ru.hh.search.httprpc;

import com.google.common.util.concurrent.ListenableFuture;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import org.testng.annotations.Test;
import ru.hh.search.httprpc.netty.NettyClient;
import ru.hh.search.httprpc.netty.TcpOptions;
import static org.testng.Assert.fail;

public class ConnectionFailTest {

  @Test(expectedExceptions = ExecutionException.class)
  public void noServer() throws ExecutionException, InterruptedException {
    InetSocketAddress address = new InetSocketAddress(12346);
    NettyClient client = new NettyClient(TcpOptions.create(), "base", 2, new JavaSerializerFactory());

    try {
      ClientMethod method = client.createMethod(RPC.signature("abracadabra", Object.class, Object.class));
      @SuppressWarnings({"unchecked"}) 
      ListenableFuture future = method.call(address, new Envelope(10, "asdfa"), "hello");
      future.get();
      fail();
    } finally {
      client.stopAndWait();
    }
  }
}
