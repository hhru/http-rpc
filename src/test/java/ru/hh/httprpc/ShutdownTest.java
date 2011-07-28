package ru.hh.httprpc;

import com.google.common.util.concurrent.ListenableFuture;
import org.testng.annotations.Test;

import java.net.ConnectException;
import java.util.concurrent.ExecutionException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.*;

public class ShutdownTest extends AbstractClientServerTest {
  @Test
  public void test() throws Exception {
    SleeperServerMethod serverMethod = new SleeperServerMethod();
    serverHandler.register(LONG2LONG_METHOD, serverMethod);

    ClientMethod<Long, Long> clientMethod = client.createMethod(LONG2LONG_METHOD);

    ListenableFuture<Long> future = clientMethod.call(address, new Envelope(), 100L); // do a (non-instant) call,
    server.stopAndWait(); // and immediately shutdown the server, waiting for live connections to complete

    assertTrue(serverMethod.completedWithin(1, SECONDS)); // ensure call went through
    assertEquals(future.get(1, SECONDS), new Long(100)); // and returned something meaninful

    ListenableFuture<Long> failed = clientMethod.call(address, new Envelope(), 100L); // a call done after shutdown,
    try {
      failed.get(1, SECONDS);
      fail();
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof ConnectException); // should FAIL!
    }
  }
}
