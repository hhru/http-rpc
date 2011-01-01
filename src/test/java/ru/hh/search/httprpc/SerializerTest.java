package ru.hh.search.httprpc;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

public class SerializerTest {
  @DataProvider(name = "serializers")
  public Object[][] serializers() {
    return new Object[][] {
      {new JavaSerializer(), new Object[] {"hello"}}
    };
  }
  
  @Test(dataProvider = "serializers")
  public void fromTo(Serializer serializer, Object[] objects) {
    for (Object o : objects) {
      assertEquals(serializer.fromBytes(serializer.toBytes(o), o.getClass()), o);
    }
  }
}
