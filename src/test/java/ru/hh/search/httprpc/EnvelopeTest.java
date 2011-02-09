package ru.hh.search.httprpc;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

public class EnvelopeTest extends AbstractClientServerTest {
  @Test
  public void test() throws ExecutionException, InterruptedException {
    RPC<Void, Envelope> signature = RPC.signature("method", Void.class, Envelope.class);
    
    server.register(signature, new EchoEnvelopeMethod());

    ClientMethod<Void, Envelope> clientMethod = client.createMethod(signature);
    
    Envelope envelope = new Envelope(123, "sjdflaskjdfas");
    assertEquals(clientMethod.call(address, envelope, null).get(), envelope);
  }
  
  private static class EchoEnvelopeMethod implements ServerMethod<Void, Envelope> {
    @Override
    public ListenableFuture<Envelope> call(Envelope envelope, Void argument) {
      return Futures.immediateFuture(envelope);
    }
  }
}
