package ru.hh.search.httprpc;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

public class SerializerTest {
  @DataProvider(name = "serializers")
  public Object[][] serializers() {
    return new Object[][] {
      {new JavaSerializer(), new Object[] {"hello"}},
      {new ProtobufSerializer(Messages.Request.getDefaultInstance()), 
        new Object[] {Messages.Request.newBuilder().setRequest("hello").build(), 
        Messages.Reply.newBuilder().setReply("world").build()}}
    };
  }
  
  @Test(dataProvider = "serializers")
  public void fromTo(Serializer serializer, Object[] objects) {
    for (Object object : objects) {
      byte[] bytes = serializer.toBytes(object);
      assertEquals(serializer.fromBytes(bytes, 0, bytes.length), object);
    }
  }
}
