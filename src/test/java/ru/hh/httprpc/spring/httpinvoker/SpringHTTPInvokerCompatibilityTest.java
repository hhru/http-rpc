package ru.hh.httprpc.spring.httpinvoker;

import static com.google.common.util.concurrent.Futures.immediateFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.springframework.remoting.httpinvoker.HttpInvokerProxyFactoryBean;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.Test;
import ru.hh.httprpc.Envelope;
import ru.hh.httprpc.JavaSerializer;
import ru.hh.httprpc.RPC;
import ru.hh.httprpc.RPCServer;
import ru.hh.httprpc.ServerMethod;
import ru.hh.httprpc.TcpOptions;

public class SpringHTTPInvokerCompatibilityTest {
  private interface TestRPCInterface {
    String ping(String data);
  }

  private interface TestRPCAPI {
    RPC<String, String> PING = RPC.signature("ping", String.class, String.class);
  }

  @Test
  public void roundTrip() throws Exception {
    TcpOptions serverOptions = TcpOptions.create().localAddress(new InetSocketAddress(InetAddress.getLocalHost(), 0));

    SpringHTTPIndapter adapter = new SpringHTTPIndapter();
    adapter.register(TestRPCAPI.PING, new ServerMethod<String, String>() {
      public ListenableFuture<String> call(Envelope envelope, String argument) {
        return immediateFuture(argument);
      }
    });

    RPCServer server = new RPCServer(serverOptions, "/", 2, new JavaSerializer());
    server.register(SpringHTTPIndapter.signature("httpinvoker"), adapter);
    server.startAndWait();

    HttpInvokerProxyFactoryBean invokerFactory = new HttpInvokerProxyFactoryBean();
    invokerFactory.setServiceUrl("http:/" + server.getLocalAddress() + "/httpinvoker");
    System.out.println(invokerFactory.getServiceUrl());
    invokerFactory.setServiceInterface(TestRPCInterface.class);
    invokerFactory.afterPropertiesSet();

    TestRPCInterface remoteService = (TestRPCInterface) invokerFactory.getObject();

    assertEquals(remoteService.ping("WTF?!"), "WTF?!");
  }
}
