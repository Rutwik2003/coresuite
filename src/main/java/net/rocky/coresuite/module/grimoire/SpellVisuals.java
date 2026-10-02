package net.rocky.coresuite.module.grimoire;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.*;
import org.bukkit.util.Vector;

/** Cosmetic choreography, driven by the existing spell tick; never spawns entities or tasks. */
final class SpellVisuals {
  private record Echo(Location at, String spell, Vector normal, boolean impact, int age) {}
  private final List<Echo> echoes = new ArrayList<>();
  private final GrimoireModule module;
  private int remaining;
  SpellVisuals(GrimoireModule module) { this.module = module; }

  static Color color(String spell) {
    return Color.fromRGB(switch (spell) {
      case "fireball" -> 0xFF783A;
      case "water-cannon" -> 0x35CFFF;
      case "frost-lance" -> 0xB4EEFF;
      case "zoltraak" -> 0xCEACFF;
      case "dimension-door" -> 0xAC65FF;
      case "aura-of-the-hearth" -> 0xFFD68A;
      case "harvesters-touch" -> 0x8CDE77;
      default -> 0x63FFE3;
    });
  }

  void pulse(Location at, String spell, Vector normal, boolean impact) {
    if (module.getConfig().getBoolean("visuals.enabled", true) && echoes.size() < 64)
      echoes.add(new Echo(at.clone(), spell, normal.clone().normalize(), impact, 0));
  }

  void tick() {
    remaining = module.getConfig().getBoolean("visuals.enabled", true)
        ? Math.max(64, Math.min(1600, module.getConfig().getInt("visuals.points-per-tick", 700))) : 0;
    for (var it = echoes.listIterator(); it.hasNext();) {
      Echo e = it.next();
      if (e.age >= 16 || !e.at.getWorld().isChunkLoaded(e.at.getBlockX() >> 4, e.at.getBlockZ() >> 4)) {
        it.remove(); continue;
      }
      if (e.age % 2 == 0) {
        double r = e.impact ? .25 + e.age * .085 : .75 - e.age * .025;
        circle(e.at, e.normal, r, e.age * .10, e.spell, 20, .85f);
        if (!e.impact) {
          circle(e.at, e.normal, r * .65, -e.age * .12, e.spell, 12, .6f);
          Vector u = side(e.normal), v = e.normal.clone().crossProduct(u).normalize();
          for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + e.age * .1;
            dust(e.at.clone().add(u.clone().multiply(Math.cos(a)*r*1.2))
                .add(v.clone().multiply(Math.sin(a)*r*1.2)), e.spell, 1.2f);
          }
        }
      }
      it.set(new Echo(e.at, e.spell, e.normal, e.impact, e.age + 1));
    }
  }

  static Vector side(Vector direction) {
    Vector side = direction.clone().crossProduct(new Vector(0, 1, 0));
    return side.lengthSquared() < .001 ? new Vector(1, 0, 0) : side.normalize();
  }

  void trail(Location at, Vector direction, String spell, int age) {
    Vector u = side(direction), v = direction.clone().crossProduct(u).normalize();
    double angle = age * .55;
    switch (spell) {
      case "fireball" -> {
        spark(at, Particle.FLAME);
        for (int i=0;i<3;i++) {
          double a=angle+i*Math.PI*2/3;
          dust(at.clone().add(u.clone().multiply(Math.cos(a)*.24)).add(v.clone().multiply(Math.sin(a)*.24)),spell,1.2f);
        }
        if(age%3==0) spark(at,Particle.SMOKE);
      }
      case "water-cannon" -> {
        for(int i=0;i<2;i++) {
          double a=angle+i*Math.PI;
          dust(at.clone().add(u.clone().multiply(Math.cos(a)*.38)).add(v.clone().multiply(Math.sin(a)*.38)),spell,.95f);
        }
        if(age%4==0) circle(at,direction,.38,angle,spell,8,.65f);
      }
      case "frost-lance" -> {
        dust(at,spell,1.3f);
        if(age%2==0) for(int i=0;i<4;i++) {
          double a=i*Math.PI/2;
          dust(at.clone().add(u.clone().multiply(Math.cos(a)*.3)).add(v.clone().multiply(Math.sin(a)*.3)),spell,.7f);
        }
        if(age%4==0) spark(at,Particle.SNOWFLAKE);
      }
      case "zoltraak" -> {
        spark(at,Particle.END_ROD);
        for(int i=-1;i<=1;i+=2)
          dust(at.clone().add(u.clone().multiply(i*.22)).add(v.clone().multiply(Math.sin(angle)*.13)),spell,.8f);
        if(age%6==0) circle(at,direction,.48,angle,spell,12,.65f);
      }
      default -> {
        dust(at,spell,1.1f);
        dust(at.clone().add(u.multiply(Math.cos(angle)*.18)).add(v.multiply(Math.sin(angle)*.18)),spell,.6f);
      }
    }
  }

  void sanctuary(Location at, double radius, long age) {
    circle(at.clone().add(0,.12,0),new Vector(0,1,0),radius,age*.018,"aura-of-the-hearth",40,.8f);
    circle(at.clone().add(0,.15,0),new Vector(0,1,0),radius*.82,-age*.025,"aura-of-the-hearth",28,.55f);
    for(int i=0;i<6;i++) {
      double a=i*Math.PI/3+age*.018;
      Location pillar=at.clone().add(Math.cos(a)*radius,.25+(age%24)*.035,Math.sin(a)*radius);
      dust(pillar,"aura-of-the-hearth",1f);
      if(age%12==0) spark(pillar,Particle.END_ROD);
    }
  }

  void circle(Location at, Vector normal, double radius, double rotation, String spell, int points, float size) {
    Vector u=side(normal), v=normal.clone().crossProduct(u).normalize();
    for(int i=0;i<points && remaining>0;i++) {
      double a=rotation+i*Math.PI*2/points;
      dust(at.clone().add(u.clone().multiply(Math.cos(a)*radius)).add(v.clone().multiply(Math.sin(a)*radius)),spell,size);
    }
  }
  private void dust(Location at,String spell,float size) {
    if(remaining-- <= 0) return;
    at.getWorld().spawnParticle(Particle.DUST,at,1,0,0,0,0,new Particle.DustOptions(color(spell),size));
  }
  private void spark(Location at,Particle particle) {
    if(remaining-- <= 0) return;
    at.getWorld().spawnParticle(particle,at,1,0,0,0,0);
  }
  void clear() { echoes.clear(); }
}
