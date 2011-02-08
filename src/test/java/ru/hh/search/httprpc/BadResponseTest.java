package ru.hh.search.httprpc;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
import org.testng.annotations.Test;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class BadResponseTest extends AbstractClientServerTest {
  @Test
  public void testNoFuture() throws InterruptedException {
    Serializer<Object> serializer = new JavaSerializer<Object>();
    String path = "/throwMethod";
    server.register(path, new ThrowMethod(), serializer, serializer);
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
  
  // TODO add test method thad returns failed future
}
