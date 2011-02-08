package ru.hh.search.httprpc;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

public class RequestReplyTest extends AbstractClientServerTest {

  @DataProvider(name = "methods")
  public Object[][] methods() {
    return new Object[][] {
      {new JavaSerializer(), new JavaSerializer(), new JavaMethod(), "hello"},
      {new ProtobufSerializer(Messages.Request.getDefaultInstance()), 
        new ProtobufSerializer(Messages.Reply.getDefaultInstance()), 
        new ProtobufMethod(), 
        Messages.Request.newBuilder().setRequest("hello").build()}
    };
  }
  
  @Test(dataProvider = "methods")
  public void test(Serializer inputSerializer, Serializer outputSerializer, ServerMethod serverMethod, Object argument) throws ExecutionException, InterruptedException {
    String path = "/helloMethod";
    server.register(path, serverMethod, outputSerializer, inputSerializer);

    Object local = serverMethod.call(null, argument).get();
    
    ClientMethod clientMethod = client.createMethod(path, inputSerializer, outputSerializer);
    Object remote = clientMethod.call(address, new Envelope(10, "asdf"), argument).get();
    
    assertEquals(remote, local);
  }

  public static class JavaMethod implements ServerMethod<String, String> {
    
    public ListenableFuture<String> call(Envelope envelope, String argument) {
      return Futures.immediateFuture(argument.toUpperCase());
    }
  }

  public static class ProtobufMethod implements ServerMethod<Messages.Reply, Messages.Request> {
    @Override
    public ListenableFuture<Messages.Reply> call(Envelope envelope, Messages.Request argument) {
      return Futures.immediateFuture(Messages.Reply.newBuilder().setReply(argument.getRequest().toUpperCase()).build());
    }
  }
}
