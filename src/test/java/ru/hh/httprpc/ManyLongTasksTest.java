package ru.hh.httprpc;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.Test;

public class ManyLongTasksTest extends AbstractClientServerTest {
  @Test
  public void test() throws ExecutionException, TimeoutException, InterruptedException {
    RPC<Long, Long> signature = RPC.signature("method", Long.class, Long.class);
    
    CountDownLatch completed = new CountDownLatch(1);
    server.register(signature, new LongJavaMethod(serverMethodExecutor, completed, new CountDownLatch(0)));

    ClientMethod<Long, Long> clientMethod = client.createMethod(signature);
    
    Envelope envelope = new Envelope(10, "qwerty");
    
    // assume that we have more serverMethodThreads than number of slow tasks
    assertTrue(serverMethodThreads > ioThreads + 1);
    
    List<Future> longFutures = new LinkedList<Future>();
    // flood server's ioThreads with long tasks (if it processes them in ioThreads) 
    for (int i = 0; i < ioThreads + 1; i++) {
      longFutures.add(clientMethod.call(address, envelope, 10000L));
    }
    
    assertEquals(clientMethod.call(address, envelope, 1L).get(1, TimeUnit.SECONDS), new Long(1));
    assertTrue(completed.await(1, TimeUnit.SECONDS));
    
    for (Future future : longFutures) {
      future.cancel(true);
    }
  }
}
