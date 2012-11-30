package ru.hh.httprpc.exporter;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hh.httprpc.Envelope;
import ru.hh.httprpc.RPC;
import ru.hh.httprpc.ServerMethod;
import ru.hh.httprpc.serialization.ProtobufSerializer;

public class RPCServiceExporter extends AbstractRPCServiceExporter<Service> {
  private static final Logger log = LoggerFactory.getLogger(RPCServiceExporter.class);

  public RPCServiceExporter() {
    super(new ProtobufSerializer());
  }

  @Override
  protected void registerService(final Service service) {
    String serviceName = service.getDescriptorForType().getName();
    for (final Descriptors.MethodDescriptor methodDescriptor : service.getDescriptorForType().getMethods()) {
      String methodName = methodDescriptor.getName();
      final String path  = String.format("/%s/%s", serviceName, methodName);

      @SuppressWarnings("unchecked")
      Class<Message> i = (Class<Message>) service.getRequestPrototype(methodDescriptor).getClass();
      @SuppressWarnings("unchecked")
      Class<Message> o = (Class<Message>) service.getResponsePrototype(methodDescriptor).getClass();
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
      log.info(String.format("Method %s was registered at path %s", methodName, path));
    }
  }

}
