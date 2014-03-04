package ru.hh.httprpc;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import static com.google.common.collect.Lists.newArrayList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.channel.ChannelHandler;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.testng.Assert.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.Test;
import ru.hh.httprpc.serialization.JavaSerializer;
import ru.hh.httprpc.util.netty.RoutingHandler;

public class FloodTest extends AbstractClientServerTest {
  @Test
  public void test() throws ExecutionException, TimeoutException, InterruptedException {
    SleeperServerMethod serverMethod = new SleeperServerMethod();
    serverHandler.register(LONG2LONG_METHOD, serverMethod);

    ClientMethod<Long, Long> clientMethod = client.createMethod(LONG2LONG_METHOD);

    List<Future> longFutures = newArrayList();
    // flood server's ioThreads with long tasks (if it processes them in ioThreads) 
    for (int i = 0; i < ioThreads + 1; i++) {
      longFutures.add(clientMethod.call(address, new Envelope(), 10000L));
    }

    // Now send a fast task and check it ran successfully
    assertEquals(clientMethod.call(address, new Envelope(), 1L).get(1, SECONDS), new Long(1));
    assertTrue(serverMethod.completedWithin(1, SECONDS));

    // Cancel long ones
    for (Future longFuture : longFutures)
      longFuture.cancel(true);
  }

  @Test
  public void rateLimitHandlerTest() throws UnknownHostException, InterruptedException, TimeoutException, ExecutionException {
    final int maxTasks = 5;
    final AtomicInteger counter = new AtomicInteger();
    SleeperServerMethod serverMethod = new SleeperServerMethod() {
      @Override
      public ListenableFuture<Long> call(Envelope envelope, Long argument) {
        if (counter.getAndIncrement() < maxTasks) {
          return super.call(envelope, argument);
        } else {
          return Futures.immediateFailedFuture(new TooManyRequestsException("limit: " + maxTasks));
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
      assertEquals(e.getCause().getClass(), TooManyRequestsException.class);
    }

    for (Future longFuture : longFutures)
      longFuture.cancel(true);
  }

  @Test
  public void rateLimitTest() throws UnknownHostException, InterruptedException {
    int maxTasks = 5;

    SleeperServerMethod serverMethod = new SleeperServerMethod();
    TcpOptions serverOptions = TcpOptions.create().localAddress(new InetSocketAddress(InetAddress.getLocalHost(), 0));
    RoutingHandler router = new RoutingHandler(
        ImmutableMap.<String, ChannelHandler>builder().put(basePath, serverHandler).build());
    server = new HTTPServer(serverOptions, ioThreads, maxTasks, router);
    server.startAsync().awaitRunning();
    address = server.getLocalAddress();
    client = new RPCClient(TcpOptions.create(), basePath, ioThreads, new JavaSerializer());

    serverHandler.register(LONG2LONG_METHOD, serverMethod);
    ClientMethod<Long, Long> clientMethod = client.createMethod(LONG2LONG_METHOD);

    List<Future> longFutures = newArrayList();
    // flood server's ioThreads with long tasks
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
      assertEquals(e.getCause().getClass(), TooManyRequestsException.class);
    }

    for (Future longFuture : longFutures)
      longFuture.cancel(true);
  }
}
