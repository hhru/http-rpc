package ru.hh.httprpc.spring.httpinvoker;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.util.concurrent.Futures;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.Serializable;
import java.util.Map;
import ru.hh.httprpc.Envelope;
import ru.hh.httprpc.RPC;
import ru.hh.httprpc.ServerMethod;
import ru.hh.httprpc.util.concurrent.CallingThreadExecutor;

public class SpringHTTPIndapter implements ServerMethod<RemoteInvocation, RemoteInvocationResult> {
  public static RPC<RemoteInvocation, RemoteInvocationResult> signature(String path) {
    return RPC.signature(path, RemoteInvocation.class, RemoteInvocationResult.class);
  }

  Map<String, HttpInvokerMethod> methods;

  private static class HttpInvokerMethod {
    public final ServerMethod implementation;
    public Class<?> parameterType;
    public final boolean varargs;

    private HttpInvokerMethod(ServerMethod implementation, boolean varargs) {
      this.implementation = implementation;
      this.varargs = varargs;
    }
  }

  public <I, O> void register(RPC<I, O> signature, ServerMethod<I, O> method) {
    new HttpInvokerMethod(method, signature.inputClass.isArray() && signature.inputClass.getComponentType().equals(Object.class));
  }

  public ListenableFuture<RemoteInvocationResult> call(Envelope envelope, RemoteInvocation remoteInvocation) {
    String methodName = remoteInvocation.methodName;

    HttpInvokerMethod method = methods.get(methodName);
    if (method == null)
      return immediateFailedFuture(new NoSuchMethodException(remoteInvocation.toString()));

    Object arguments;

    if (method.varargs)
      arguments = remoteInvocation.arguments;
    else if (
        remoteInvocation.parameterTypes.length != 1 ||
        remoteInvocation.arguments.length != 1 ||
        !method.parameterType.equals(remoteInvocation.parameterTypes[0]) ||
        !method.parameterType.isInstance(remoteInvocation.arguments[0])
        )
      return immediateFailedFuture(new NoSuchMethodException(remoteInvocation.toString()));
    else
      arguments = remoteInvocation.arguments[0];

    return Futures.compose(
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

/**
 * This class was taken from Spring, package org.springframework.remoting.support and stripped down to absolute minimum
 * still preserving serialized form compatibility
 */
@SuppressWarnings({"UnusedDeclaration", "FieldCanBeLocal"})
class RemoteInvocationResult implements Serializable {
  private static final long serialVersionUID = 2138555143707773549L;

  private Object value;
  private Throwable exception;

  public RemoteInvocationResult(Object value) {
    this.value = value;
  }

  public RemoteInvocationResult(Throwable exception) {
    this.exception = exception;
  }
}

/**
 * This class was taken from Spring, package org.springframework.remoting.support and stripped down to absolute minimum
 * still preserving serialized form compatibility
 */
@SuppressWarnings("UnusedDeclaration")
class RemoteInvocation implements Serializable {
  private static final long serialVersionUID = 6876024250231820554L;

  public String methodName;
  public Class[] parameterTypes;
  public Object[] arguments;
  public Map attributes;

  public String toString() {
    return methodName + "(" + Joiner.on(", ").join(parameterTypes) + ")";
  }
}
