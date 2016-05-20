package com.xjeffrose.chicago.client;

import com.netflix.curator.test.TestingServer;
import com.xjeffrose.chicago.Chicago;
import java.net.InetSocketAddress;
import java.util.Random;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChicagoClientTest {
  static TestingServer testingServer;
  static Chicago chicago1;
  static Chicago chicago2;
  static Chicago chicago3;
  static Chicago chicago4;

  static ChicagoClient chicagoClientSingle;
  static ChicagoClient chicagoClientDHT;

  @BeforeClass
  static public void setupFixture() throws Exception {
//    testingServer = new TestingServer(2182);
//    chicago1 = new Chicago();
//    chicago1.main(new String[]{"", "src/test/resources/test1.conf"});
//    chicago2 = new Chicago();
//    chicago2.main(new String[]{"", "src/test/resources/test2.conf"});
//    chicago3 = new Chicago();
//    chicago3.main(new String[]{"", "src/test/resources/test3.conf"});
//    chicago4 = new Chicago();
//    chicago4.main(new String[]{"", "src/test/resources/test4.conf"});
//    chicagoClientSingle = new ChicagoClient(new InetSocketAddress("127.0.0.1", 12000));
    chicagoClientDHT = new ChicagoClient("10.25.160.234:2181");
//    chicagoClientDHT = new ChicagoClient("10.22.100.183:2181");
//    chicagoClientDHT = new ChicagoClient(testingServer.getConnectString());
//    chicagoClientDHT = new ChicagoClient("10.24.25.188:2181,10.24.25.189:2181,10.25.145.56:2181,10.24.33.123:2181");

  }

  @Test
  public void transactOnce() throws Exception {
    for (int i = 0; i < 1; i++) {
      String _k = "key" + i;
      byte[] key = _k.getBytes();
      String _v = "val" + i;
      byte[] val = _v.getBytes();
      assertEquals(true, chicagoClientDHT.write(key, val));
      assertEquals(new String(val), new String(chicagoClientDHT.read(key)));
//      chicagoClientDHT.read(key);
      assertEquals(true, chicagoClientDHT.delete(key));
    }
  }

  @Test
  public void transactMany() throws Exception {
    for (int i = 0; i < 20; i++) {
      String _k = "key" + i;
      byte[] key = _k.getBytes();
      String _v = "val" + i;
      byte[] val = _v.getBytes();
      assertEquals(true, chicagoClientDHT.write(key, val));
      assertEquals(new String(val), new String(chicagoClientDHT.read(key)));
//      chicagoClientDHT.read(key);
      assertEquals(true, chicagoClientDHT.delete(key));
    }
  }

  @Test
  public void transactManyCF() throws Exception {
    for (int i = 0; i < 20; i++) {
      String _k = "key" + i;
      byte[] key = _k.getBytes();
      String _v = "val" + i;
      byte[] val = _v.getBytes();
      assertEquals(true, chicagoClientDHT.write("colfam".getBytes(), key, val));
      assertEquals(new String(val), new String(chicagoClientDHT.read("colfam".getBytes(), key)));
//     chicagoClientDHT.read("colfam".getBytes(), key);
      assertEquals(true, chicagoClientDHT.delete("colfam".getBytes(), key));

    }
  }

  @Test
  public void transactManyCFConcurrecnt() throws Exception {
    for (int i = 0; i < 100; i++) {
      String _k = "key" + i;
      byte[] key = _k.getBytes();
      String _v = "val" + i;
      byte[] val = _v.getBytes();
      new Thread(new Runnable() {
        @Override
        public void run() {
          assertEquals(true, chicagoClientDHT.write("colfam".getBytes(), key, val));
          assertEquals(new String(val), new String(chicagoClientDHT.read("colfam".getBytes(), key)));
//     chicagoClientDHT.read("colfam".getBytes(), key);
          assertEquals(true, chicagoClientDHT.delete("colfam".getBytes(), key));
        }
      }).start();
    }
  }

}