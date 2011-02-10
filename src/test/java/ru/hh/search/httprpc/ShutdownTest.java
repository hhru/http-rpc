package ru.hh.search.httprpc;

import com.google.common.util.concurrent.ListenableFuture;
import java.net.ConnectException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.testng.annotations.Test;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class ShutdownTest extends AbstractClientServerTest {
  @Test()
  public void test() throws ExecutionException, TimeoutException, InterruptedException {
    RPC<Long, Long> signature = RPC.signature("method", Long.class, Long.class);
    CountDownLatch completed = new CountDownLatch(1);
    CountDownLatch interrupted = new CountDownLatch(1);
    server.register(signature, new LongJavaMethod(serverMethodExecutor, completed, interrupted));
    ClientMethod<Long, Long> clientMethod = client.createMethod(signature);
    ListenableFuture<Long> future = clientMethod.call(address, new Envelope(10, "qwerty"), 100L);
    server.stopAndWait();
    ListenableFuture<Long> failed = clientMethod.call(address, new Envelope(10, "qwerty"), 100L);
    assertNotNull(future.get(1, TimeUnit.SECONDS));
    assertTrue(completed.await(1, TimeUnit.SECONDS));
    try {
      failed.get(1, TimeUnit.SECONDS);
      fail();
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof ConnectException);
    }
  }
}
