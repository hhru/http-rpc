package ru.hh.httprpc;

import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import ru.hh.httprpc.serialization.JavaBaseSerializer;
import ru.hh.jetty.HHServerConnector;

public abstract class AbstractJettyClientServerTest {
  protected String basePath = "/apiBase";
  protected RPCJettyServlet servlet;
  protected ServerConnector connector;

  private Server server;

  @BeforeMethod
  public void start() throws Exception {
    servlet = new RPCJettyServlet(new JavaBaseSerializer());
    server = new Server();

    ServletContextHandler handler = new ServletContextHandler();
    handler.setContextPath("/");
    handler.addServlet(new ServletHolder(servlet), basePath + "/*");
    server.setHandler(handler);

    this.connector = new HHServerConnector(
        server, -1, -1, new HttpConnectionFactory(new HttpConfiguration())
    );
    this.connector.setPort(0);
    server.addConnector(this.connector);
    server.start();
  }

  @AfterMethod
  public void stop() throws Exception {
    server.stop();
    server.join();
  }
}
