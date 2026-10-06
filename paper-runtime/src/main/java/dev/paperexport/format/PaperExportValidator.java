package dev.paperexport.format;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static dev.paperexport.format.PaperEntityDefinition.*;

public final class PaperExportValidator {
    private static final Pattern ID=Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern SEG=Pattern.compile("[a-z0-9_.-]+");
    private PaperExportValidator() {}
    private static void require(boolean condition,String message) { if(!condition)throw new IllegalArgumentException(message); }
    private static boolean number(double n,double min,double max) {return Double.isFinite(n)&&n>=min&&n<=max;}
    private static boolean vec(double[] v,int count,double min,double max){if(v==null||v.length!=count)return false;for(double n:v)if(!number(n,min,max))return false;return true;}
    public static boolean safePath(String path){if(path==null||path.length()>240||path.isEmpty()||path.startsWith("/")||path.contains("\\")||path.contains(":")||path.indexOf('\0')>=0)return false;for(String seg:path.split("/",-1))if(!SEG.matcher(seg).matches()||seg.equals(".")||seg.equals(".."))return false;return true;}
    public static void validate(PaperEntityDefinition d){
        Manifest m=d.manifest;require(m!=null&&"paperexport".equals(m.format),"manifest.json: invalid format");
        require(m.format_version==1,"manifest.json: unsupported format version "+m.format_version);
        require(m.id!=null&&ID.matcher(m.id).matches()&&!m.id.contains("//")&&!m.id.contains(".."),"manifest.json: invalid ID");
        require("1.21.11".equals(m.minecraft_version)&&"1.21.11".equals(m.paper_version),"manifest.json: requires 1.21.11");
        require("model/model.json".equals(m.model)&&"entity/entity.json".equals(m.entity)&&"resourcepack/".equals(m.resource_pack),"manifest.json: invalid fixed paths");
        require(m.name!=null&&m.name.length()<=256&&m.author!=null&&m.description!=null,"manifest.json: invalid metadata");
        require(m.textures!=null&&m.animations!=null,"manifest.json: missing textures or animations");
        for(var e:m.textures.entrySet()){Texture t=e.getValue();require(SEG.matcher(e.getKey()).matches()&&t!=null&&("textures/"+e.getKey()+".png").equals(t.path)&&t.width>0&&t.width<=2048&&t.height>0&&t.height<=2048,"manifest.json: invalid texture "+e.getKey());}
        if(m.sounds!=null){require(m.sounds.size()<=32,"manifest.json: too many sounds");for(var entry:m.sounds.entrySet()){Sound sound=entry.getValue();require(SEG.matcher(entry.getKey()).matches()&&sound!=null&&("sounds/"+entry.getKey()+".ogg").equals(sound.path),"manifest.json: invalid sound "+entry.getKey());}}
        for(var e:m.animations.entrySet())require(SEG.matcher(e.getKey()).matches()&&("animations/"+e.getKey()+".json").equals(e.getValue()),"manifest.json: invalid animation path");
        require(d.model!=null&&vec(d.model.texture_size,2,1,2048)&&d.model.bones!=null&&!d.model.bones.isEmpty()&&d.model.bones.size()<=128,"model/model.json: invalid model");
        Set<String> ids=new HashSet<>();
        for(Bone b:d.model.bones){
            require(b!=null&&b.id!=null&&SEG.matcher(b.id).matches()&&ids.add(b.id),"model/model.json: duplicate/invalid bone ID");
            require(b.name!=null&&b.name.length()<=128&&vec(b.pivot,3,-1024,1024)&&vec(b.rotation,3,-36000,36000)&&vec(b.scale,3,.001,100)&&b.cubes!=null&&b.cubes.size()<=256,"model/model.json: invalid bone "+b.id);
            for(Cube c:b.cubes){require(c!=null&&c.name!=null&&vec(c.from,3,-1024,1024)&&vec(c.to,3,-1024,1024)&&c.faces!=null,"model/model.json: invalid cube in "+b.id);for(int i=0;i<3;i++)require(c.from[i]<c.to[i],"model/model.json: reversed cube coordinates");
                for(var f:c.faces.entrySet())require(List.of("north","south","east","west","up","down").contains(f.getKey())&&f.getValue()!=null&&m.textures.containsKey(f.getValue().texture)&&vec(f.getValue().uv,4,-2048,2048)&&List.of(0,90,180,270).contains(f.getValue().rotation),"model/model.json: invalid face in "+b.id);
            }
        }
        Map<String,Bone> bones=d.model.bones.stream().collect(java.util.stream.Collectors.toMap(b->b.id,b->b));
        for(Bone b:d.model.bones){Set<String> seen=new HashSet<>();Bone cursor=b;while(cursor!=null){require(seen.add(cursor.id),"model/model.json: bone cycle at "+b.id);if(cursor.parent!=null)require(bones.containsKey(cursor.parent),"model/model.json: missing parent of "+cursor.id);cursor=cursor.parent==null?null:bones.get(cursor.parent);}}
        if(d.model.hitboxes!=null){require(d.model.hitboxes.size()<=64,"model/model.json: too many hitboxes");Set<String> hitboxIds=new HashSet<>();for(HitboxRegion h:d.model.hitboxes){require(h!=null&&h.id!=null&&SEG.matcher(h.id).matches()&&hitboxIds.add(h.id)&&bones.containsKey(h.bone)&&vec(h.from,3,-1024,1024)&&vec(h.to,3,-1024,1024),"model/model.json: invalid pe_hitbox");for(int i=0;i<3;i++)require(h.from[i]<h.to[i],"model/model.json: reversed pe_hitbox");require(h.to[1]-h.from[1]<=512&&Math.max(h.to[0]-h.from[0],h.to[2]-h.from[2])<=512,"model/model.json: pe_hitbox exceeds size limit");}}
        Entity e=d.entity;require(e!=null&&List.of("ZOMBIE","SKELETON","HUSK","STRAY","PIG","COW","ARMOR_STAND","INTERACTION").contains(e.base_entity)&&List.of("passive","neutral","hostile","stationary","flying","swimming","boss","npc","custom").contains(e.behavior)&&e.stats!=null&&e.hitbox!=null,"entity/entity.json: invalid entity");
        Stats s=e.stats;require(number(s.max_health,1,2048)&&number(s.movement_speed,0,2)&&number(s.attack_damage,0,2048)&&number(s.follow_range,0,256)&&number(s.knockback_resistance,0,1)&&number(s.armor,0,30)&&number(s.armor_toughness,0,20)&&number(s.scale,.01,16),"entity/entity.json: invalid stats");
        require(number(e.hitbox.width,.05,32)&&number(e.hitbox.height,.05,32)&&number(e.hitbox.vertical_offset,-16,16)&&number(e.hitbox.interaction_range,0,64),"entity/entity.json: invalid hitbox");
        if(e.sound_cues!=null)for(var cue:e.sound_cues.entrySet())
            require(List.of("spawn","ambient","attack","hurt","death").contains(cue.getKey())&&cue.getValue()!=null&&m.sounds!=null&&m.sounds.containsKey(cue.getValue()),"entity/entity.json: unknown sound cue "+cue.getKey());
        if(e.boss!=null){Boss boss=e.boss;
            require(boss.title!=null&&boss.title.length()<=128&&boss.bar_color!=null&&List.of("PINK","BLUE","RED","GREEN","YELLOW","PURPLE","WHITE").contains(boss.bar_color)&&boss.bar_style!=null&&List.of("SOLID","SEGMENTED_6","SEGMENTED_10","SEGMENTED_12","SEGMENTED_20").contains(boss.bar_style)&&number(boss.range,4,128)&&boss.phases!=null&&boss.phases.size()<=8,"entity/entity.json: invalid boss settings");
            double previous=1;
            for(Phase phase:boss.phases){
                require(phase!=null&&number(phase.below_health,.01,.99)&&phase.below_health<previous,"entity/entity.json: invalid boss phase threshold");
                if(phase.animation!=null)require(m.animations.containsKey(phase.animation),"entity/entity.json: unknown phase animation "+phase.animation);
                if(phase.sound!=null)require(m.sounds!=null&&m.sounds.containsKey(phase.sound),"entity/entity.json: unknown phase sound "+phase.sound);
                previous=phase.below_health;
            }
        }
        require(d.animations!=null&&d.animations.keySet().equals(m.animations.keySet()),"animations: references missing");
        for(var entry:d.animations.entrySet()){Animation a=entry.getValue();require(a!=null&&entry.getKey().equals(a.name)&&number(a.length,.001,3600)&&List.of("once","hold","loop").contains(a.loop)&&a.tracks!=null&&a.tracks.size()<=256&&a.events!=null&&a.events.size()<=1024,"animations/"+entry.getKey()+".json: invalid animation");
            for(Track t:a.tracks){require(t!=null&&bones.containsKey(t.bone),"animations/"+entry.getKey()+".json: missing bone "+(t==null?"null":t.bone));for(List<Key> keys:List.of(t.position==null?List.<Key>of():t.position,t.rotation==null?List.<Key>of():t.rotation,t.scale==null?List.<Key>of():t.scale)){require(keys.size()<=4096,"animation has too many keys");double prev=-1;for(Key k:keys){require(k!=null&&number(k.time,0,a.length)&&k.time>prev&&vec(k.value,3,-36000,36000)&&(k.interpolation==null||List.of("linear","step").contains(k.interpolation)),"animation has invalid keyframe");prev=k.time;}}}
            for(Event event:a.events){
                require(event!=null&&number(event.time,0,a.length)&&event.type!=null&&List.of("sound","particle","damage","custom_event").contains(event.type)&&event.data!=null,"animation has invalid event");
                for(Object value:event.data.values())require(value instanceof String||value instanceof Number||value instanceof Boolean,"animation event data must be primitive");
                if("custom_event".equals(event.type))require(event.data.get("key") instanceof String key&&ID.matcher(key).matches(),"custom event needs a namespaced key");
            }
        }
    }
}
