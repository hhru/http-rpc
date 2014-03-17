package ru.hh.httprpc.util.netty;

import com.google.common.base.Charsets;
import static com.google.common.base.Preconditions.checkArgument;
import com.google.common.base.Throwables;
import static java.lang.String.format;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
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
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.HOST;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.jboss.netty.handler.codec.http.QueryStringEncoder;

public class Http {
  public static HttpRequestBuilder request(HttpMethod method, String uri) {
    return new HttpRequestBuilder(method, uri);
  }

  public static HttpRequestBuilder request(HttpMethod method, UrlBuilder url) {
    return new HttpRequestBuilder(method, url.toString());
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

    public SELF host(String hostName) {
      message.setHeader(HOST, hostName);

      return self;
    }

    public SELF header(String name, Object value) {
      message.headers().set(name, value);

      return self;
    }

    public SELF header(Map<String, ?> values) {
      for (Map.Entry<String, ?> entry : values.entrySet()) {
        message.headers().set(entry.getKey(), entry.getValue());
      }

      return self;
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
    HttpRequestBuilder(HttpMethod method, String uri) {
      super(new DefaultHttpRequest(HTTP_1_1, method, uri));
    }
  }

  public static final class HttpResponseBuilder extends HttpMessageBuilder<HttpResponse, HttpResponseBuilder> {
    HttpResponseBuilder(HttpResponseStatus status) {
      super(new DefaultHttpResponse(HTTP_1_1, status));
    }
  }

  public static UrlBuilder url(String path) {
    return new UrlBuilder(path);
  }

  public static final class UrlBuilder {
    private final QueryStringEncoder encoder;

    UrlBuilder(String path) {
      encoder = new QueryStringEncoder(path, Charsets.UTF_8);
    }

    public UrlBuilder param(String name, Object value) {
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

  public static class UrlDecoder {
    private final QueryStringDecoder decoder;

    public UrlDecoder(String url) {
      decoder = new QueryStringDecoder(url, Charsets.UTF_8);
    }

    public String path() {
      return decoder.getPath();
    }

    public String singleString(String name) {
      String value = optionalSingleString(name);
      checkArgument(value != null, "'%s' is expected to be a single-valued string, actual: <null>", name);
      return value;
    }

    public String optionalSingleString(String name) {
      List<String> values = decoder.getParameters().get(name);

      if (values == null || values.size() == 0)
        return null;

      checkArgument(values.size() == 1, "'%s' is expected to be a single-valued string, actual: %s", name, values);

      return values.get(0);
    }

    public String optionalSingleString(String name, String def) {
      String value = optionalSingleString(name);
      return value != null ? value : def;
    }

    public int singleInt(String name) {
      Integer value = optionalSingleInt(name);
      checkArgument(value != null, "'%s' is expected to be a single-valued int, actual: <null>", name);
      return value;
    }

    public Integer optionalSingleInt(String name) {
      List<String> values = decoder.getParameters().get(name);

      if (values == null || values.size() == 0)
        return null;

      checkArgument(values.size() == 1, "'%s' is expected to be a single-valued int, actual: %s", name, values);
      try {
        return Integer.valueOf(values.get(0));
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(format("'%s' is expected to be a single-valued int, actual: %s", name, values.get(0)));
      }
    }

    public Integer optionalSingleInt(String name, Integer def) {
      Integer value = optionalSingleInt(name);
      return value != null ? value : def;
    }

    public long singleLong(String name) {
      Long value = optionalSingleLong(name);
      checkArgument(value != null, "'%s' is expected to be a single-valued long, actual: <null>", name);
      return value;
    }

    public Long optionalSingleLong(String name) {
      List<String> values = decoder.getParameters().get(name);

      if (values == null || values.size() == 0)
        return null;

      checkArgument(values.size() == 1, "'%s' is expected to be a single-valued long, actual: %s", name, values);
      try {
        return Long.valueOf(values.get(0));
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(format("'%s' is expected to be a single-valued long, actual: %s", name, values.get(0)));
      }
    }

    public Long optionalSingleLong(String name, Long def) {
      Long value = optionalSingleLong(name);
      return value != null ? value : def;
    }
  }
}
