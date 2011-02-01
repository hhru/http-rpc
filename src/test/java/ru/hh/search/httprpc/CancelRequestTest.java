package ru.hh.search.httprpc;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.fail;

public class CancelRequestTest extends AbstractClientServerTest {
  @DataProvider(name = "times")
  public Object[][] times() {
    return new Object[][] {
      {0, 1000},
      {1, 1000},
      {100, 1000}
    };
  }
  
  @Test(expectedExceptions = CancellationException.class, dataProvider = "times") 
  public void test(long clientTime, long serverTime) throws ExecutionException, InterruptedException {
    String path = "method";
    Serializer serializer = new JavaSerializer();
    server.register(path, new LongJavaMethod(), serializer, serializer);
    ClientMethod<Long, Object> clientMethod = client.createMethod(path, serializer, serializer);

    ListenableFuture<Long> result = clientMethod.call(address, new Envelope(10, "asdf"), serverTime);
    if (clientTime > 0) {
      Thread.sleep(clientTime);
    }
    result.cancel(true);
    result.get();
    fail();
  }

  public static class LongJavaMethod implements ServerMethod<Long, Long> {
    public static final Logger logger = LoggerFactory.getLogger(LongJavaMethod.class); 
    
    @Override
    public Long call(Envelope envelope, Long argument) {
      try {
        Thread.sleep(argument);
        return argument;
      } catch (InterruptedException e) {
        throw Throwables.propagate(e);
      }
    }
  }
}