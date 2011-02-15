package ru.hh.search.httprpc;

import org.jboss.netty.buffer.ChannelBuffer;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class SerializerTest {
  @DataProvider(name = "serializers")
  public Object[][] serializers() {
    return new Object[][] {
      {new JavaSerializer(), new Object[] {"hello"}},
      {new ProtobufSerializer(), new Object[] {Messages.Request.newBuilder().setRequest("hello").build()}}
    };
  }
  
  @Test(dataProvider = "serializers")
  public void fromTo(Serializer factory, Object[] objects) throws SerializationException {
    for (Object object : objects) {
      Serializer.ForClass serializer = factory.forClass(object.getClass());
      @SuppressWarnings("unchecked")
      ChannelBuffer serialForm = serializer.serialize(object);
      assertEquals(serializer.deserialize(serialForm), object);
    }
  }
}
