package ru.hh.httprpc.serialization;

import static org.testng.Assert.assertEquals;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import ru.hh.httprpc.Messages;

public class SerializerTest {
  @DataProvider(name = "serializers")
  public Object[][] serializers() {
    return new Object[][]{
        {new JavaSerializer(), new Object[]{"hello"}},
        {new ProtobufSerializer(), new Object[]{Messages.Request.newBuilder().setRequest("hello").build()}}
    };
  }

  @Test(dataProvider = "serializers")
  public void fromTo(Serializer serializer, Object[] objects) throws SerializationException {
    for (Object object : objects) {
      @SuppressWarnings("unchecked")
      Class<Object> clazz = (Class<Object>) object.getClass();
      assertEquals(serializer.decoder(clazz).apply(serializer.encoder(clazz).apply(object)), object);
    }
  }
}
