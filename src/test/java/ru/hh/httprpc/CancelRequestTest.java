package ru.hh.httprpc;

import com.google.common.util.concurrent.ListenableFuture;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class CancelRequestTest extends AbstractClientServerTest {
  @DataProvider(name = "times")
  public Object[][] times() {
    return new Object[][]{
        {0, 1000},
        {1, 1000},
        {10, 1000},
        {100, 1000},
        {300, 1000},
        {300, 10},
        {300, 100}
    };
  }

  @Test(dataProvider = "times")
  public void test(long clientTimeout, long serverSleep) throws Exception {
    SleeperServerMethod serverMethod = new SleeperServerMethod();

    serverHandler.register(LONG2LONG_METHOD, serverMethod);
    ClientMethod<Long, Long> clientMethod = client.createMethod(LONG2LONG_METHOD);

    ListenableFuture<Long> result = clientMethod.call(address, new Envelope(), serverSleep);
    Thread.sleep(clientTimeout);
    result.cancel(true);

    if (clientTimeout < serverSleep) { // if client sleeps less than server,
      assertFalse(serverMethod.completed()); // the method should never complete

      if (serverMethod.started()) // if the call actually went through to server,
        assertTrue(serverMethod.interruptedWithin(10, MILLISECONDS)); // ensure it was interrupted

    } else {
      // Though, it's totally other case if the server had enough time!
      assertTrue(serverMethod.completed());
      assertFalse(serverMethod.interrupted());
    }
  }
}
