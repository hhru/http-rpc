package ru.hh.search.httprpc;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import java.net.URI;
import java.net.URISyntaxException;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import static org.jboss.netty.buffer.ChannelBuffers.copiedBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import static org.jboss.netty.channel.ChannelFutureListener.CLOSE;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import org.jboss.netty.handler.codec.http.QueryStringEncoder;

public class Http {
  public static HttpRequestBuilder request(HttpMethod method, String uri) {
    return new HttpRequestBuilder(method, uri);
  }

  public static HttpRequestBuilder request(HttpMethod method, UriBuilder uri) {
    return new HttpRequestBuilder(method, uri.toString());
  }

  public static HttpResponseBuilder response(HttpResponseStatus status) {
    return new HttpResponseBuilder(status);
  }

  private static class HttpMessageBuilder<MESSAGE extends HttpMessage, SELF extends HttpMessageBuilder<MESSAGE, SELF>> {
    @SuppressWarnings("unchecked")
    private final SELF self = (SELF) this;
    protected final MESSAGE message;

    protected HttpMessageBuilder(MESSAGE message) {
      this.message = message;
      HttpHeaders.setKeepAlive(message, false);
    }

    public SELF containing(String contentType, ChannelBuffer buffer) {
      message.setHeader(CONTENT_TYPE, contentType);
      message.setHeader(CONTENT_LENGTH, buffer.readableBytes());
      message.setContent(buffer);

      return self;
    }

    public SELF containing(String contentType, byte[] buffer) {
      return containing(contentType, ChannelBuffers.wrappedBuffer(buffer));
    }

    public SELF containing(String content) {
      return containing("text/plain; charset=UTF-8", copiedBuffer(content, Charsets.UTF_8));
    }

    public SELF containing(Throwable throwable) {
      return containing(Throwables.getStackTraceAsString(throwable));
    }

    public ChannelFuture sendTo(Channel channel) {
      return sendTo(channel, false);
    }

    public ChannelFuture sendAndClose(Channel channel) {
      return sendTo(channel, true);
    }

    private ChannelFuture sendTo(Channel channel, boolean close) {
      if (message.getContent().capacity() == 0)
        containing(message.toString());

      ChannelFuture f = channel.write(message);
      if (close)
        f.addListener(CLOSE);
      return f;
    }
  }

  public static final class HttpRequestBuilder extends HttpMessageBuilder<HttpRequest, HttpRequestBuilder> {
    protected HttpRequestBuilder(HttpMethod method, String uri) {
      super(new DefaultHttpRequest(HTTP_1_1, method, uri));
    }
  }

  public static final class HttpResponseBuilder extends HttpMessageBuilder<HttpResponse, HttpResponseBuilder> {
    private HttpResponseBuilder(HttpResponseStatus status) {
      super(new DefaultHttpResponse(HTTP_1_1, status));
    }
  }

  public static UriBuilder uri(String path) {
    return new UriBuilder(path);
  }

  public static final class UriBuilder {
    private final QueryStringEncoder encoder;

    public UriBuilder(String path) {
      encoder = new QueryStringEncoder(path);
    }

    public UriBuilder param(String name, Object value) {
      encoder.addParam(name, value.toString());
      return this;
    }

    public String toString() {
      return encoder.toString();
    }

    public URI toUri() throws URISyntaxException {
      return encoder.toUri();
    }
  }
}
