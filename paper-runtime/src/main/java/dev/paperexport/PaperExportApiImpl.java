package dev.paperexport;

import dev.paperexport.api.PaperExportApi;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import java.util.Collection;
import java.util.Set;

final class PaperExportApiImpl implements PaperExportApi {
    private final PaperExportPlugin plugin;
    PaperExportApiImpl(PaperExportPlugin plugin){this.plugin=plugin;}
    private static void mainThread(){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("PaperExport API must be called on the server main thread");}
    @Override public Collection<EntityInfo> entities(){
        mainThread();return plugin.registry().all().stream().map(d->new EntityInfo(
            d.manifest.id,d.manifest.name,d.entity.stats.max_health,d.entity.boss!=null&&d.entity.boss.enabled,
            Set.copyOf(d.animations.keySet()),Set.copyOf(d.manifest.sounds==null?Set.<String>of():d.manifest.sounds.keySet()))).toList();
    }
    @Override public Entity spawn(String entityId,Location location){
        mainThread();if(location==null||location.getWorld()==null)throw new IllegalArgumentException("Spawn location needs a world");
        var definition=plugin.registry().get(entityId);if(definition==null)throw new IllegalArgumentException("Unknown PaperExport entity: "+entityId);
        var instance=plugin.spawner().spawn(location,definition);plugin.ticker().add(instance);plugin.fireCue(instance,"spawn");return instance.controller;
    }
    @Override public boolean playAnimation(Entity controller,String animation){
        mainThread();var instance=plugin.ticker().get(controller);if(instance==null||!instance.definition.animations.containsKey(animation))return false;
        instance.animation.play(animation);return true;
    }
    @Override public boolean playSound(Entity controller,String soundName){
        mainThread();var instance=plugin.ticker().get(controller);return instance!=null&&plugin.playSound(instance,soundName);
    }
    @Override public String entityId(Entity controller){
        mainThread();var instance=plugin.ticker().get(controller);return instance==null?null:instance.definition.manifest.id;
    }
    @Override public boolean despawn(Entity controller){
        mainThread();if(plugin.ticker().get(controller)==null)return false;plugin.ticker().remove(controller);controller.remove();return true;
    }
    @Override public String reloadPackages(){mainThread();return plugin.reloadDefinitions();}
    @Override public void registerCustomEvent(Plugin owner,String eventId,CustomEventHandler handler){
        mainThread();plugin.events().register(owner,eventId,handler);
    }
    @Override public void unregisterCustomEvents(Plugin owner){mainThread();plugin.events().unregister(owner);}
}
