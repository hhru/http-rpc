package ru.hh.httprpc.util.netty;

import com.google.common.base.Charsets;
import static com.google.common.base.Preconditions.checkArgument;
import com.google.common.base.Throwables;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import static java.lang.String.format;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import static io.netty.channel.ChannelFutureListener.CLOSE;
import io.netty.handler.codec.http.HttpHeaders;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaders.Names.HOST;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.QueryStringEncoder;

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

  private static class HttpMessageBuilder<MESSAGE extends FullHttpMessage, SELF extends HttpMessageBuilder<MESSAGE, SELF>> {
    @SuppressWarnings("unchecked")
    private final SELF self = (SELF) this;
    protected final MESSAGE message;

    protected HttpMessageBuilder(MESSAGE message) {
      this.message = message;
      HttpHeaders.setKeepAlive(message, false);
    }

    public SELF host(String hostName) {
      message.headers().set(HOST, hostName);

      return self;
    }

    public SELF containing(String contentType, ByteBuf buffer) {
      message.headers().set(CONTENT_TYPE, contentType);
      message.headers().set(CONTENT_LENGTH, buffer.readableBytes());
      message.content().capacity(buffer.capacity() - buffer.readerIndex());
      message.content().clear();
      buffer.getBytes(0, message.content());

      return self;
    }

    public SELF containing(String contentType, byte[] buffer) {
      return containing(contentType, Unpooled.wrappedBuffer(buffer));
    }

    public SELF containing(String content) {
      return containing("text/plain; charset=UTF-8", Unpooled.copiedBuffer(content, Charsets.UTF_8));
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
      if (message.content().capacity() == 0) {
        containing(message.toString());
      }

      ChannelFuture f = channel.writeAndFlush(message);
      if (close) {
        f.addListener(CLOSE);
      }
      return f;
    }
  }

  public static final class HttpRequestBuilder extends HttpMessageBuilder<FullHttpRequest, HttpRequestBuilder> {
    HttpRequestBuilder(HttpMethod method, String uri) {
      super(new DefaultFullHttpRequest(HTTP_1_1, method, uri));
    }
  }

  public static final class HttpResponseBuilder extends HttpMessageBuilder<FullHttpResponse, HttpResponseBuilder> {
    HttpResponseBuilder(HttpResponseStatus status) {
      super(new DefaultFullHttpResponse(HTTP_1_1, status));
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
      return decoder.path();
    }

    public String singleString(String name) {
      String value = optionalSingleString(name);
      checkArgument(value != null, "'%s' is expected to be a single-valued string, actual: <null>", name);
      return value;
    }

    public String optionalSingleString(String name) {
      List<String> values = decoder.parameters().get(name);

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
      List<String> values = decoder.parameters().get(name);

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
      List<String> values = decoder.parameters().get(name);

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
