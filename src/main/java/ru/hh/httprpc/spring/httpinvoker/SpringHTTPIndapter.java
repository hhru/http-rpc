package ru.hh.httprpc.spring.httpinvoker;

import com.google.common.base.Function;
import com.google.common.collect.MapMaker;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.springframework.remoting.support.RemoteInvocation;
import org.springframework.remoting.support.RemoteInvocationResult;
import ru.hh.httprpc.Envelope;
import ru.hh.httprpc.RPC;
import ru.hh.httprpc.ServerMethod;
import ru.hh.httprpc.util.concurrent.CallingThreadExecutor;

import java.util.concurrent.ConcurrentMap;

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;

public class SpringHTTPIndapter implements ServerMethod<RemoteInvocation, RemoteInvocationResult> {
  public static RPC<RemoteInvocation, RemoteInvocationResult> signature(String path) {
    return RPC.signature(path, RemoteInvocation.class, RemoteInvocationResult.class);
  }

  private final ConcurrentMap<String, HttpInvokerMethod> methods = new MapMaker().makeMap();

  private static class HttpInvokerMethod {
    public final ServerMethod implementation;
    public final Class<?> parameterType;
    public final boolean varargs;

    private <T> HttpInvokerMethod(ServerMethod<T, ?> implementation, Class<T> parameterType) {
      this.implementation = implementation;
      this.parameterType = parameterType;
      this.varargs = parameterType.isArray() && parameterType.getComponentType().equals(Object.class);
    }
  }

  public <I, O> void register(RPC<I, O> signature, ServerMethod<I, O> method) {
    methods.put(signature.path, new HttpInvokerMethod(method, signature.inputClass));
  }

  public ListenableFuture<RemoteInvocationResult> call(Envelope envelope, RemoteInvocation remoteInvocation) {
    String methodName = remoteInvocation.getMethodName();

    HttpInvokerMethod method = methods.get(methodName);
    if (method == null)
      return immediateFailedFuture(new NoSuchMethodException(remoteInvocation.toString()));

    Object arguments;

    if (method.varargs)
      arguments = remoteInvocation.getArguments();
    else if (
        remoteInvocation.getParameterTypes().length != 1 ||
        remoteInvocation.getArguments().length != 1 ||
        !method.parameterType.equals(remoteInvocation.getParameterTypes()[0]) ||
        !method.parameterType.isInstance(remoteInvocation.getArguments()[0])
        )
      return immediateFailedFuture(new NoSuchMethodException(remoteInvocation.toString()));
    else
      arguments = remoteInvocation.getArguments()[0];

    return Futures.transform(
        method.implementation.call(envelope, arguments),
        new Function<Object, RemoteInvocationResult>() {
          public RemoteInvocationResult apply(Object result) {
            return new RemoteInvocationResult(result);
          }
        },
        CallingThreadExecutor.instance()
    );
  }
}
