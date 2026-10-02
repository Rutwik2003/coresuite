package net.rocky.coresuite.module.grimoire;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.junit.jupiter.api.Test;

class SpellTargetTest {
  @Test void animalsAreConfigurableAndProtectedTargetsStayExcluded() {
    var module = mock(GrimoireModule.class);
    var config = new YamlConfiguration();
    when(module.getConfig()).thenReturn(config);
    var engine = new SpellEngine(module);
    var caster = mock(Player.class);
    assertTrue(engine.validTarget(caster, mock(Chicken.class)));
    assertTrue(engine.validTarget(caster, mock(Sheep.class)));
    assertTrue(engine.validTarget(caster, mock(Zombie.class)));
    assertTrue(engine.validTarget(caster, mock(Ghast.class)));
    var pet = mock(Wolf.class);
    when(pet.isTamed()).thenReturn(true);
    assertFalse(engine.validTarget(caster, pet));
    assertFalse(engine.validTarget(caster, mock(Player.class)));
    assertFalse(engine.validTarget(caster, mock(Villager.class)));
    var npc = mock(Chicken.class);
    when(npc.hasMetadata("NPC")).thenReturn(true);
    assertFalse(engine.validTarget(caster, npc));
    config.set("targeting.animals", false);
    assertFalse(engine.validTarget(caster, mock(Chicken.class)));
    assertTrue(engine.validTarget(caster, mock(Zombie.class)));
  }
}
