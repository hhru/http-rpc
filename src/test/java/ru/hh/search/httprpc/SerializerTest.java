package ru.hh.search.httprpc;

import org.jboss.netty.buffer.ChannelBuffer;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class SerializerTest {
  @DataProvider(name = "serializers")
  public Object[][] serializers() {
    return new Object[][] {
      {new JavaSerializerFactory(), new Object[] {"hello"}},
      {new ProtobufSerializerFactory(), new Object[] {Messages.Request.newBuilder().setRequest("hello").build()}}
    };
  }
  
  @Test(dataProvider = "serializers")
  public void fromTo(SerializerFactory factory, Object[] objects) throws SerializationException {
    for (Object object : objects) {
      Serializer serializer = factory.createForClass(object.getClass());
      @SuppressWarnings("unchecked")
      ChannelBuffer serialForm = serializer.serialize(object);
      assertEquals(serializer.deserialize(serialForm), object);
    }
  }
}
