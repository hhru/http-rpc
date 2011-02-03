package ru.hh.search.httprpc;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.testng.annotations.Test;
import static org.testng.AssertJUnit.assertEquals;

public class ManyLongTasksTest extends AbstractClientServerTest {
  @Test
  public void test() throws ExecutionException, TimeoutException, InterruptedException {
    String path = "method";
    Serializer serializer = new JavaSerializer();
    
    server.register(path, new LongJavaMethod(), serializer, serializer);

    @SuppressWarnings({"unchecked"}) 
    ClientMethod clientMethod = client.createMethod(path, serializer, serializer);
    
    Envelope envelope = new Envelope(10, "qwerty");
    
    // flood server's ioThreads with long tasks (if it processes them in ioThreads) 
    for (int i = 0; i < ioThreads + 1; i++) {
      clientMethod.call(address, envelope, new Long(10000));
    }
    
    assertEquals(clientMethod.call(address, envelope, 1).get(1, TimeUnit.SECONDS), 1);
  }
}
