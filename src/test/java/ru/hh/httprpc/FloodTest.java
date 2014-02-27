package ru.hh.httprpc;

import static com.google.common.collect.Lists.newArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.concurrent.TimeoutException;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.Test;

public class FloodTest extends AbstractClientServerTest {
  @Test
  public void test() throws ExecutionException, TimeoutException, InterruptedException {
    SleeperServerMethod serverMethod = new SleeperServerMethod();
    serverHandler.register(LONG2LONG_METHOD, serverMethod);

    ClientMethod<Long, Long> clientMethod = client.createMethod(LONG2LONG_METHOD);

    List<Future> longFutures = newArrayList();
    // flood server's ioThreads with long tasks (if it processes them in ioThreads) 
    for (int i = 0; i < ioThreads + 1; i++)
      longFutures.add(clientMethod.call(address, new Envelope(), 10000L));

    // Now send a fast task and check it ran successfully
    assertEquals(clientMethod.call(address, new Envelope(), 1L).get(1, SECONDS), new Long(1));
    assertTrue(serverMethod.completedWithin(1, SECONDS));

    // Cancel long ones
    for (Future longFuture : longFutures)
      longFuture.cancel(true);
  }
}
