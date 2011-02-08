package ru.hh.search.httprpc;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

public class EnvelopeTest extends AbstractClientServerTest {
  @Test
  public void test() throws ExecutionException, InterruptedException {
    String path = "method";
    Serializer serializer = new JavaSerializer();
    
    server.register(path, new EchoEnvelopeMethod(), serializer, serializer);

    @SuppressWarnings({"unchecked"}) 
    ClientMethod clientMethod = client.createMethod(path, serializer, serializer);
    
    Envelope envelope = new Envelope(123, "sjdflaskjdfas");
    assertEquals(clientMethod.call(address, envelope, null).get(), envelope);
  }
  
  private static class EchoEnvelopeMethod implements ServerMethod<Envelope, Void> {
    @Override
    public ListenableFuture<Envelope> call(Envelope envelope, Void argument) {
      return Futures.immediateFuture(envelope);
    }
  }
}
