package ru.hh.httprpc;

import com.google.common.base.Throwables;
import static com.google.common.collect.Lists.newArrayList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import static io.netty.handler.codec.http.HttpResponseStatus.SERVICE_UNAVAILABLE;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.Test;

public class FloodTest extends AbstractClientServerTest {
  {
    concurrentLimit = ioThreads + 1;
  }
  @Test
  public void test() throws ExecutionException, TimeoutException, InterruptedException {
    SleeperServerMethod serverMethod = new SleeperServerMethod();
    serverHandler.register(LONG2LONG_METHOD, serverMethod);

    ClientMethod<Long, Long> clientMethod = client.createMethod(LONG2LONG_METHOD);

    List<Future> longFutures = newArrayList();
    // flood server's ioThreads with long tasks (if it processes them in ioThreads) 
    for (int i = 0; i < concurrentLimit - 1; i++) {
      longFutures.add(clientMethod.call(address, new Envelope(), 10000L));
    }

    // Now send a fast task and check it ran successfully
    assertEquals(clientMethod.call(address, new Envelope(), 1L).get(1, SECONDS), new Long(1));
    assertTrue(serverMethod.completedWithin(1, SECONDS));

    // Cancel long ones
    for (Future longFuture : longFutures) {
      longFuture.cancel(true);
    }
  }

  @Test
  public void rateLimitHandlerTest() throws UnknownHostException, InterruptedException, TimeoutException, ExecutionException {
    final int maxTasks = concurrentLimit - 1;
    final AtomicInteger counter = new AtomicInteger();
    SleeperServerMethod serverMethod = new SleeperServerMethod() {
      @Override
      public ListenableFuture<Long> call(Envelope envelope, Long argument) {
        if (counter.getAndIncrement() < maxTasks) {
          return super.call(envelope, argument);
        } else {
          return Futures.immediateFailedFuture(new RPCMethodException(SERVICE_UNAVAILABLE, "limit: " + maxTasks));
        }
      }
    };
    serverHandler.register(LONG2LONG_METHOD, serverMethod);

    ClientMethod<Long, Long> clientMethod = client.createMethod(LONG2LONG_METHOD);

    List<Future> longFutures = newArrayList();
    // flood server's ioThreads with long tasks (if it processes them in ioThreads)
    for (int i = 0; i < maxTasks; i++) {
      longFutures.add(clientMethod.call(address, new Envelope(), 10000L));
    }

    // wait till requests start on server
    TimeUnit.MILLISECONDS.sleep(100);

    // one more request
    ListenableFuture<Long> future = clientMethod.call(address, new Envelope(), 10000L);
    try {
      future.get();
      assertFalse(true); // shouldn't get here
    } catch (InterruptedException e) {
      Throwables.propagate(e);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      assertEquals(cause.getClass(), BadResponseException.class);
      BadResponseException response = (BadResponseException) cause;
      assertTrue(response.getMessage().contains(SERVICE_UNAVAILABLE.reasonPhrase()));
    }

    for (Future longFuture : longFutures)
      longFuture.cancel(true);
  }
}
