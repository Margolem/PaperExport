package dev.paperexport;

import dev.paperexport.format.PaperEntityDefinition.Event;
import dev.paperexport.api.PaperExportApi;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Interprets only allowlisted data events. No commands or script execution. */
public final class PaperEventDispatcher {
    private final PaperExportPlugin plugin;
    private record Registration(Plugin owner,PaperExportApi.CustomEventHandler handler) {}
    private final Map<String,List<Registration>> handlers=new HashMap<>();
    public PaperEventDispatcher(PaperExportPlugin plugin){this.plugin=plugin;}
    public void register(Plugin owner,String eventId,PaperExportApi.CustomEventHandler handler){
        if(owner==null||!owner.isEnabled()||handler==null||eventId==null||!eventId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Custom event needs an enabled plugin, namespaced ID, and handler");
        handlers.computeIfAbsent(eventId,ignored->new ArrayList<>()).add(new Registration(owner,handler));
    }
    public void unregister(Plugin owner){for(var listeners:handlers.values())listeners.removeIf(item->item.owner==owner);}
    public boolean playSound(PaperEntitySpawner.Instance instance,String name){
        if(name==null||instance.definition.manifest.sounds==null||!instance.definition.manifest.sounds.containsKey(name))return false;
        String[] id=instance.definition.manifest.id.split(":",2);
        String key=id[0]+":paperexport."+id[1]+"."+name;
        instance.lastLocation.getWorld().playSound(instance.lastLocation,key,SoundCategory.HOSTILE,1f,1f);
        return true;
    }
    public void playCue(PaperEntitySpawner.Instance instance,String cue){
        Map<String,String> cues=instance.definition.entity.sound_cues;
        if(cues!=null)playSound(instance,cues.get(cue));
    }
    public void fire(PaperEntitySpawner.Instance instance,Event event){
        var world=instance.lastLocation.getWorld();Map<String,Object> data=event.data;
        switch(event.type){
            case "sound"->{String key=String.valueOf(data.getOrDefault("key",""));if(key.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))world.playSound(instance.lastLocation,key,SoundCategory.HOSTILE,clamp(data.get("volume"),0,2,1),clamp(data.get("pitch"),.5f,2,1));}
            case "particle"->{String key=String.valueOf(data.getOrDefault("particle",""));try{Particle particle=Particle.valueOf(key.toUpperCase(java.util.Locale.ROOT));int count=(int)clamp(data.get("count"),0,100,1);world.spawnParticle(particle,instance.lastLocation,count);}catch(IllegalArgumentException ignored){}}
            case "damage"->{if(!plugin.getConfig().getBoolean("events.allow-package-damage",false))return;double radius=clamp(data.get("radius"),0,8,0),amount=clamp(data.get("damage"),0,20,0);if(radius<=0||amount<=0)return;for(var target:world.getNearbyEntities(instance.lastLocation,radius,radius,radius))if(target instanceof LivingEntity living&&target!=instance.controller)living.damage(amount,instance.controller);}
            case "custom_event"->{
                String key=String.valueOf(data.getOrDefault("key",""));
                if(!key.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))return;
                for(Registration registration:List.copyOf(handlers.getOrDefault(key,List.of()))){
                    if(!registration.owner.isEnabled())continue;
                    try{registration.handler.handle(instance.controller,key,Map.copyOf(data));}
                    catch(Exception error){plugin.getLogger().warning("Custom event handler "+registration.owner.getName()+" failed for "+key+": "+error.getMessage());}
                }
            }
            default->{}
        }
    }
    private static float clamp(Object value,float lo,float hi,float fallback){if(!(value instanceof Number n)||!Double.isFinite(n.doubleValue()))return fallback;return Math.max(lo,Math.min(hi,n.floatValue()));}
}
