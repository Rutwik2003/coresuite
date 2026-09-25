package net.rocky.coresuite.module.worldbosses.combat;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BossMusicDefaultsTest {
 @Test void updatesStockTracksButKeepsCustomTimingsAndMusic() {
  var c = new YamlConfiguration();
  c.set("borealis.bgm", "minecraft:music_disc.otherside");
  c.set("valerius.bgm-p1", "coresuite:boss.valerius.p1"); c.set("valerius.bgm-p1-duration-seconds",200);
  c.set("aethelgard.bgm-p1", "custom:track"); c.set("aethelgard.bgm-p1-duration-seconds",259);
  c.set("xolthos.bgm-p1", "coresuite:boss.xolthos.p1"); c.set("xolthos.bgm-p1-duration-seconds",300);
  assertTrue(BossMusicDefaults.upgrade(c));
  assertEquals("coresuite:boss.borealis.p3",c.getString("borealis.bgm-p3"));
  assertEquals(201,c.getInt("valerius.bgm-p1-duration-seconds"));
  assertEquals(259,c.getInt("aethelgard.bgm-p1-duration-seconds"));
  assertEquals(300,c.getInt("xolthos.bgm-p1-duration-seconds"));
  assertFalse(BossMusicDefaults.upgrade(c));
 }
}
