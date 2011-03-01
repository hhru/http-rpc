package ru.hh.httprpc.util.netty;

import com.google.common.base.Supplier;
import static com.google.common.collect.Lists.newArrayList;
import java.util.Collection;
import java.util.List;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.util.internal.ConversionUtil;

public class GuavyChannelPipelineFactory implements ChannelPipelineFactory {
  private final List<Supplier<? extends ChannelHandler>> suppliers = newArrayList();

  public void add(Supplier<? extends ChannelHandler> handlerSupplier) {
    suppliers.add(handlerSupplier);
  }

  public void addAll(Collection<? extends Supplier<? extends ChannelHandler>> handlerSuppliers) {
    suppliers.addAll(handlerSuppliers);
  }

  public ChannelPipeline getPipeline() throws Exception {
    ChannelPipeline pipeline = Channels.pipeline();

    for (int i = 0; i < suppliers.size(); i++)
      pipeline.addLast(ConversionUtil.toString(i), suppliers.get(i).get() ); // Blatantly stolen from Channels.pipeline(ChannelHandler[])

    return pipeline;
  }
}
