package net.rocky.coresuite.module.grimoire;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class SpellVisualsTest {
  @Test void verticalShotsHaveFinitePerpendicularBasis() {
    for (Vector direction : new Vector[]{new Vector(0,1,0),new Vector(0,-1,0),new Vector(1,0,0)}) {
      Vector side=SpellVisuals.side(direction);
      assertEquals(1,side.length(),.000001);
      assertEquals(0,side.dot(direction),.000001);
    }
  }
  @Test void particleBudgetIsSharedAndExpiredAnimationsStop() {
    var module=mock(GrimoireModule.class);
    var config=new YamlConfiguration();config.set("visuals.points-per-tick",64);
    when(module.getConfig()).thenReturn(config);
    var world=mock(World.class);when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
    var effects=new SpellVisuals(module);
    Location at=new Location(world,0,70,0);
    for(int i=0;i<100;i++)effects.pulse(at,"zoltraak",new Vector(0,0,1),false);
    effects.tick();
    long particles=mockingDetails(world).getInvocations().stream().filter(i->i.getMethod().getName().equals("spawnParticle")).count();
    assertEquals(64,particles);
    for(int i=0;i<20;i++)effects.tick();
    clearInvocations(world);effects.tick();
    verifyNoInteractions(world);
    config.set("visuals.enabled",false);effects.pulse(at,"fireball",new Vector(0,1,0),true);effects.tick();
    effects.trail(at,new Vector(0,1,0),"fireball",0);
    verifyNoInteractions(world);
  }
}
