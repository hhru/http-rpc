package ru.hh.httprpc.exporter;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import ru.hh.httprpc.HTTPServer;
import ru.hh.httprpc.RPCHandler;
import ru.hh.httprpc.TcpOptions;
import ru.hh.httprpc.serialization.Serializer;

public abstract class AbstractRPCServiceExporter<T> implements InitializingBean {
  private static final Logger log = LoggerFactory.getLogger(AbstractRPCServiceExporter.class);
  private HTTPServer server;
  private String host;
  private int port;
  private int ioThreadsCount;
  private boolean tcpNoDelay;
  private int backlog = 50; // 50 is default value from java.net.ServerSocket

  protected final RPCHandler handler;

  public void setServices(List<? extends T> services) {
    for (T service : services) {
      registerService(service);
    }
  }

  abstract protected void registerService(final T service);

  protected AbstractRPCServiceExporter(Serializer serializer) {
    handler = new RPCHandler(serializer);
  }

  @Override
  public void afterPropertiesSet() throws UnknownHostException {
    start(InetAddress.getByName(host));
  }

  public void startAndWait(InetAddress host) {
    initServer(host);
    server.startAndWait();
  }

  public void start(InetAddress host) {
    initServer(host);
    server.start();
  }

  public void stopAndWait() {
    server.stopAndWait();
  }

  public void stop() {
    server.stop();
  }

  private void initServer(InetAddress host) {
    InetSocketAddress address = new InetSocketAddress(host, port);
    final TcpOptions tcpOptions = TcpOptions.create()
        .localAddress(address)
        .tcpNoDelay(tcpNoDelay)
        .backlog(backlog);
    server = new HTTPServer(tcpOptions, ioThreadsCount, handler);
    log.info(String.format("Started protobuf server at %s", address.toString()));
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public void setIoThreadsCount(int ioThreadsCount) {
    this.ioThreadsCount = ioThreadsCount;
  }

  public void setTcpNoDelay(boolean tcpNoDelay) {
    this.tcpNoDelay = tcpNoDelay;
  }

  public void setBacklog(int backlog) {
    this.backlog = backlog;
  }
}
