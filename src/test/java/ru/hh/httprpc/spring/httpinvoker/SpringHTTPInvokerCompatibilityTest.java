package ru.hh.httprpc.spring.httpinvoker;

import static com.google.common.util.concurrent.Futures.immediateFuture;
import com.google.common.util.concurrent.ListenableFuture;
import org.springframework.remoting.httpinvoker.HttpInvokerProxyFactoryBean;
import org.springframework.remoting.support.RemoteInvocation;
import org.springframework.remoting.support.RemoteInvocationResult;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.Test;
import ru.hh.httprpc.AbstractClientServerTest;
import ru.hh.httprpc.Envelope;
import ru.hh.httprpc.RPC;
import ru.hh.httprpc.ServerMethod;

public class SpringHTTPInvokerCompatibilityTest extends AbstractClientServerTest {

  private interface TestRPCInterface {
    String ping(String data);
  }

  private interface TestRPCAPI {
    RPC<String, String> PING = RPC.signature("ping", String.class, String.class);
  }

  @Test
  public void roundTrip() throws Exception {

    SpringHTTPIndapter adapter = new SpringHTTPIndapter();
    adapter.register(TestRPCAPI.PING, new ServerMethod<String, String>() {
      public ListenableFuture<String> call(Envelope envelope, String argument) {
        return immediateFuture(argument);
      }
    });

    RPC<RemoteInvocation, RemoteInvocationResult> signature = SpringHTTPIndapter.signature("/httpinvoker");
    serverHandler.register(signature, adapter);

    HttpInvokerProxyFactoryBean invokerFactory = new HttpInvokerProxyFactoryBean();
    invokerFactory.setServiceUrl("http:/" + server.getLocalAddress() + basePath + signature.path);
    invokerFactory.setServiceInterface(TestRPCInterface.class);
    invokerFactory.afterPropertiesSet();

    TestRPCInterface remoteService = (TestRPCInterface) invokerFactory.getObject();

    assertEquals(remoteService.ping("WTF?!"), "WTF?!");
  }
}
