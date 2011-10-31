package ru.hh.httprpc.exporter;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.Service;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import ru.hh.httprpc.Envelope;
import ru.hh.httprpc.HTTPServer;
import ru.hh.httprpc.RPC;
import ru.hh.httprpc.RPCHandler;
import ru.hh.httprpc.ServerMethod;
import ru.hh.httprpc.TcpOptions;
import ru.hh.httprpc.serialization.ProtobufSerializer;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;

public class RPCServiceExporter implements InitializingBean {
  private final ProtobufSerializer serializer = new ProtobufSerializer();
  private HTTPServer server;
  private String host;
  private int port;
  private int ioThreadsCount;
  protected RPCHandler handler = new RPCHandler(serializer);

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
    server = new HTTPServer(TcpOptions.create().localAddress(address), ioThreadsCount, handler);
    LoggerFactory.getLogger(RPCServiceExporter.class).info(String.format("Started protobuf server at %s", address.toString()));
  }

  public void setServices(List<Service> services) {
    for (Service service : services) {
      registerService(service);
    }
  }

  @SuppressWarnings("unchecked")
  private void registerService(final Service service) {
    String serviceName = service.getDescriptorForType().getName();
    for (final Descriptors.MethodDescriptor methodDescriptor : service.getDescriptorForType().getMethods()) {
      String methodName = methodDescriptor.getName();
      final String path  = String.format("/%s/%s", serviceName, methodName);

      Class<Message> i = (Class<Message>) service.getRequestPrototype(methodDescriptor).getClass();
      Class<Message> o = (Class<Message>) service.getRequestPrototype(methodDescriptor).getClass();
      RPC<Message, Message> signature = RPC.signature(path, i, o);

      ServerMethod<Message, Message> method = new ServerMethod<Message, Message>() {
        @Override
        public ListenableFuture<Message> call(Envelope envelope, Message request) {
          final SettableFuture<Message> future = SettableFuture.create();
          final EnvelopeController controller = new EnvelopeController(envelope);

          service.callMethod(methodDescriptor, controller, request, new RpcCallback<Message>() {
            @Override
            public void run(Message parameter) {
              if (controller.failed()) {
                future.setException(controller.getReason());
              } else {
                future.set(parameter);
              }
            }
          });
          return future;
        }
      };

      handler.register(signature, method);
      LoggerFactory.getLogger(RPCServiceExporter.class).info(String.format("Method %s was registered at path %s", methodName, path));
    }
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
}
