package ru.hh.httprpc;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.MapMaker;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hh.httprpc.serialization.BaseSerializer;
import ru.hh.httprpc.util.concurrent.FutureListener;
import ru.hh.httprpc.util.netty.Http;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ConcurrentMap;

import static java.lang.String.format;
import static ru.hh.httprpc.HttpRpcNames.REQUEST_ID;
import static ru.hh.httprpc.HttpRpcNames.TIMEOUT;

public class RPCJettyServlet extends HttpServlet {
  public static final Logger logger = LoggerFactory.getLogger(RPCJettyServlet.class);

  private final BaseSerializer serializer;
  private final ConcurrentMap<String, ServerMethodDescriptor<?, ?>> methods;
  private volatile boolean prohibitCancellation = false;

  public RPCJettyServlet(BaseSerializer serializer) {
    this.serializer = serializer;
    this.methods = new MapMaker().makeMap();
  }

  public <I, O> void register(RPC<I, O> signature, ServerMethod<I, O> method) {
    methods.put(
        signature.path,
        new ServerMethodDescriptor<I, O>(
            method,
            serializer.decoder(signature.inputClass),
            serializer.encoder(signature.outputClass)
        )
    );
  }

  @Override
  protected void doPost(HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
    final String path = req.getPathInfo();
    final Http.UrlDecoder url = new Http.UrlDecoder(req.getRequestURI());
    try {
      final ServerMethodDescriptor<?, ?> descriptor = methods.get(path);
      if (descriptor == null) {
        if (!("/".equals(path))) {
          logger.error(format("method '%s' not found", path));
        }

        resp.setStatus(HttpStatus.NOT_FOUND_404);
        resp.setContentType(serializer.getContentType());
        resp.getWriter().println(format("" +
                "method '%s' not found.\n" +
                "available methods:\n" +
                "%s\n",
            path,
            methods.isEmpty() ? "<none>" : Joiner.on("\n").join(methods.keySet())
        ));

        return;
      }

      String requestId = req.getHeader(HttpRpcNames.REQUEST_ID_HEADER_NAME);
      final ListenableFuture<byte[]> methodFuture = descriptor.call(
          new Envelope(
              url.optionalSingleLong(TIMEOUT, Envelope.DEFAULT_TIMEOUT),
              requestId == null ? url.optionalSingleString(REQUEST_ID, Envelope.DEFAULT_REQUESTID) : requestId
          ),
          req.getInputStream()
      );

      new FutureListener<byte[]>(methodFuture) {
        protected void success(byte[] result) {
          try (OutputStream output = resp.getOutputStream();) {
            output.write(result);
          } catch (IOException e) {
            logger.error("Failed to write to out stream", e);
          }
        }

        protected void exception(Throwable e) {
          exceptionHandler(resp, e, path);
        }
      };

    } catch (Exception e) {
      exceptionHandler(resp, e, path);
    }
  }

  private void exceptionHandler(HttpServletResponse resp, Throwable e, String path) {
    if (e instanceof IllegalArgumentException) {
      sendError(resp, HttpStatus.BAD_REQUEST_400, e, "bad parameters for '%s'", path);
    } else {
      sendError(resp, HttpStatus.INTERNAL_SERVER_ERROR_500, e, "'%s' method future failed", path);
    }
  }

  private static void sendError(HttpServletResponse resp, int status, Throwable exception, String message, Object... args) {
    resp.setStatus(status);
    logger.error(format(message, args), exception);
    try {
      resp.getWriter().println(message);
    } catch (IOException e) {
      logger.error("Failed to write error message", e);
    }
  }

  private static class ServerMethodDescriptor<I, O> {
    final public ServerMethod<I, O> method;
    Function<InputStream, I> decoder;
    Function<O, byte[]> encoder;

    public ServerMethodDescriptor(
        ServerMethod<I, O> method,
        Function<InputStream, I> decoder,
        Function<O, byte[]> encoder
    ) {
      this.method = method;
      this.decoder = decoder;
      this.encoder = encoder;
    }

    public ListenableFuture<byte[]> call(Envelope envelope, InputStream input) {
      return Futures.transform(method.call(envelope, decoder.apply(input)), encoder);
    }
  }
}
