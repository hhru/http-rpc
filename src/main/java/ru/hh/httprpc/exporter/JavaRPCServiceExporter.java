package ru.hh.httprpc.exporter;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hh.httprpc.Envelope;
import ru.hh.httprpc.RPC;
import ru.hh.httprpc.ServerMethod;
import ru.hh.httprpc.serialization.JavaSerializer;

public class JavaRPCServiceExporter extends AbstractRPCServiceExporter<Object> {
  private static final Logger log = LoggerFactory.getLogger(JavaRPCServiceExporter.class);

  public JavaRPCServiceExporter() {
    super(new JavaSerializer());
  }

  @Override
  @SuppressWarnings("unchecked")
  protected void registerService(final Object service) {
    String serviceName = service.getClass().getSimpleName();
    for (final Method methodDescriptor : service.getClass().getDeclaredMethods()) {
      String methodName = methodDescriptor.getName();
      final String path  = String.format("/%s/%s", serviceName, methodName);

      if (!ListenableFuture.class.isAssignableFrom(methodDescriptor.getReturnType())) {
        continue;
      }
      Class i = methodDescriptor.getParameterTypes()[0];
      Class o = methodDescriptor.getReturnType();
      RPC<Serializable, Serializable> signature = RPC.signature(path, i, o);

      ServerMethod<Serializable, Serializable> method = new ServerMethod<Serializable, Serializable>() {
        @Override
        public ListenableFuture<Serializable> call(Envelope envelope, Serializable request) {
          final EnvelopeController controller = new EnvelopeController(envelope);
          Object result;
          try {
            if (methodDescriptor.getParameterTypes().length > 0) {
              if (EnvelopeController.class.isAssignableFrom(methodDescriptor.getParameterTypes()[0])) {
                if (methodDescriptor.getParameterTypes().length > 1) {
                  result = methodDescriptor.invoke(service, controller, request);
                } else {
                  result = methodDescriptor.invoke(service, controller);
                }
              } else {
                result = methodDescriptor.invoke(service, request);
              }
            } else {
              result = methodDescriptor.invoke(service);
            }
          } catch (IllegalAccessException e) {
            result = Futures.immediateFailedFuture(e);
          } catch (InvocationTargetException e) {
            result = Futures.immediateFailedFuture(e.getCause());
          }
          return (ListenableFuture<Serializable>) result;
        }
      };

      handler.register(signature, method);
      log.info(String.format("Method %s was registered at path %s", methodName, path));
    }
  }
}
