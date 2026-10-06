package dev.paperexport.format;

import java.util.List;
import java.util.Map;

/** Gson DTOs mirror PAPEREXPORT_FORMAT.md; validated before entering the registry. */
public final class PaperEntityDefinition {
    public Manifest manifest;
    public Model model;
    public Entity entity;
    public Map<String, Animation> animations;
    public Map<String, byte[]> files;

    public static final class Manifest {
        public String format, id, name, author, description, minecraft_version, paper_version, exporter_version, model, entity, resource_pack;
        public int format_version;
        public Map<String,String> animations;
        public Map<String,Texture> textures;
        public Map<String,Sound> sounds;
    }
    public static final class Texture { public String path; public int width,height; }
    public static final class Sound { public String path; }
    public static final class Model { public double[] texture_size; public List<Bone> bones; public List<HitboxRegion> hitboxes; }
    public static final class HitboxRegion { public String id,bone; public double[] from,to; }
    public static final class Bone { public String id,name,parent; public double[] pivot,rotation,scale; public List<Cube> cubes; }
    public static final class Cube { public String name; public double[] from,to; public Map<String,Face> faces; }
    public static final class Face { public double[] uv; public String texture; public int rotation; }
    public static final class Entity { public String base_entity,behavior; public Stats stats; public Hitbox hitbox; public Map<String,String> sound_cues; public Boss boss; }
    public static final class Stats { public double max_health,movement_speed,attack_damage,follow_range,knockback_resistance,armor,armor_toughness,scale; public boolean gravity,invulnerable,silent,persistent; }
    public static final class Hitbox { public double width,height,vertical_offset,interaction_range; }
    public static final class Boss { public boolean enabled; public String title,bar_color,bar_style; public double range; public List<Phase> phases; }
    public static final class Phase { public double below_health; public String animation,sound; }
    public static final class Animation { public String name,loop; public double length; public int priority; public List<Track> tracks; public List<Event> events; }
    public static final class Track { public String bone; public List<Key> position,rotation,scale; }
    public static final class Key { public double time; public double[] value; public String interpolation; }
    public static final class Event { public double time; public String type; public Map<String,Object> data; }
}
