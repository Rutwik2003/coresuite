package net.rocky.coresuite.module.worldbosses.rewards;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import net.rocky.coresuite.module.worldbosses.boss.BossType;
class BossLoreTest {
 @Test void storiesNeverNameCountersOrDamageMultipliers() {
  for(var type:BossType.values()) {
   var text=String.join(" ",BossLore.lines(type)).toLowerCase(java.util.Locale.ROOT);
   assertFalse(text.isBlank());
   for(String spoiler:new String[]{"shovel","fire resistance","night vision","splash","weakness","2.5x","use an axe","water potion"}) assertFalse(text.contains(spoiler),type+": "+spoiler);
  }
 }
}
