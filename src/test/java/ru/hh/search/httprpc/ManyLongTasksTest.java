package ru.hh.search.httprpc;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.testng.annotations.Test;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

public class ManyLongTasksTest extends AbstractClientServerTest {
  @Test
  public void test() throws ExecutionException, TimeoutException, InterruptedException {
    String path = "method";
    Serializer serializer = new JavaSerializer();
    
    CountDownLatch completed = new CountDownLatch(1);
    server.register(path, new LongJavaMethod(serverMethodExecutor, completed, new CountDownLatch(0)), 
      serializer, serializer);

    @SuppressWarnings({"unchecked"}) 
    ClientMethod clientMethod = client.createMethod(path, serializer, serializer);
    
    Envelope envelope = new Envelope(10, "qwerty");
    
    // assume that we have more serverMethodThreads than number of slow tasks
    assertTrue(serverMethodThreads > ioThreads + 1);
    
    // flood server's ioThreads with long tasks (if it processes them in ioThreads) 
    for (int i = 0; i < ioThreads + 1; i++) {
      clientMethod.call(address, envelope, 10000L);
    }
    
    assertEquals(clientMethod.call(address, envelope, 1L).get(1, TimeUnit.SECONDS), 1L);
    assertTrue(completed.await(1, TimeUnit.SECONDS));
  }
}
