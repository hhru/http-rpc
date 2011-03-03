package ru.hh.httprpc;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class CancelRequestTest extends AbstractClientServerTest {
  @DataProvider(name = "times")
  public Object[][] times() {
    return new Object[][] {
      {0, 1000},
      {1, 1000},
      {10, 1000},
      {100, 1000}
    };
  }
  
  @Test(dataProvider = "times") 
  public void test(long clientTime, long serverTime) throws ExecutionException, InterruptedException {
    RPC<Long, Long> signature = RPC.signature("method", Long.class, Long.class);
    final CountDownLatch completed = new CountDownLatch(1);
    final CountDownLatch interrupted = new CountDownLatch(1);
    serverHandler.register(signature, new LongJavaMethod(serverMethodExecutor, completed, interrupted));
    ClientMethod<Long, Long> clientMethod = client.createMethod(signature);

    ListenableFuture<Long> result = clientMethod.call(address, new Envelope(10, "asdf"), serverTime);
    if (clientTime > 0) {
      Thread.sleep(clientTime);
    }
    result.cancel(true);
    if (clientTime >= 100) {
      assertTrue(interrupted.await(10, TimeUnit.MILLISECONDS));
    }
    assertTrue(completed.getCount() == 1);
  }
}
