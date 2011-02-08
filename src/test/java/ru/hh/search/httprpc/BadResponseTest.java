package ru.hh.search.httprpc;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class BadResponseTest extends AbstractClientServerTest {
  @DataProvider(name = "methods")
  public Object[][] methods() {
    return new Object[][] {
      {new ThrowMethod()},
      {new FailedFutureMethod()}
    };
  }
  
  @Test(dataProvider = "methods")
  public void test(ServerMethod<Object, String> method) throws InterruptedException {
    Serializer<Object> serializer = new JavaSerializer<Object>();
    String path = "/throwMethod";
    server.register(path, method, serializer, serializer);
    ClientMethod<Object, String> clientMethod = client.<Object, String>createMethod(path, serializer, serializer);

    String message = "message to be returned as exception";
    try {
      clientMethod.call(address, new Envelope(123, "123"), message).get();
      fail();
    } catch (ExecutionException e) {
      if (e.getCause() instanceof BadResponseException) {
        BadResponseException cause = (BadResponseException) e.getCause();
        assertTrue(cause.getMessage().contains("500"));
        assertTrue(cause.getDetails().contains(message));
      } else {
        fail("unexpected cause", e.getCause());
      }
    }
  }
  
  private static class ThrowMethod implements ServerMethod<Object, String> {
    @Override
    public ListenableFuture<Object> call(Envelope envelope, String message) {
      throw new RuntimeException(message);
    }
  }
  
  private static class FailedFutureMethod implements ServerMethod<Object, String> {

    @Override
    public ListenableFuture<Object> call(Envelope envelope, String message) {
      return Futures.immediateFailedFuture(new RuntimeException(message));
    }
  }
}
