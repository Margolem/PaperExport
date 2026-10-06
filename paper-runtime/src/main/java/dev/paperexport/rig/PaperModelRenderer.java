package dev.paperexport.rig;

import dev.paperexport.PaperExportPlugin;
import dev.paperexport.format.PaperEntityDefinition;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Matrix4f;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PaperModelRenderer {
    private final PaperExportPlugin plugin;
    private final Map<UUID,Matrix4f> lastTransforms=new HashMap<>();
    public PaperModelRenderer(PaperExportPlugin plugin){this.plugin=plugin;}
    public Map<String,ItemDisplay> spawn(org.bukkit.entity.Entity controller,PaperEntityDefinition definition,UUID instanceId){
        Map<String,ItemDisplay> parts=new HashMap<>();String[] id=definition.manifest.id.split(":",2);
        for(var bone:definition.model.bones){if(bone.cubes.isEmpty())continue;
            String key="paperexport/"+id[1]+"/"+bone.id;
            ItemStack item=ItemStack.of(Material.PAPER);ItemMeta meta=item.getItemMeta();
            if(plugin.version().legacyItemModels()){
                Integer modelData=plugin.pack().legacyModelData().get(definition.manifest.id+"/"+bone.id);
                if(modelData==null)throw new IllegalStateException("Missing legacy model data for "+definition.manifest.id+"/"+bone.id);
                meta.setCustomModelData(modelData);
            }else meta.setItemModel(new NamespacedKey(id[0],key));
            item.setItemMeta(meta);
            ItemDisplay display=controller.getWorld().spawn(displayLocation(controller.getLocation()),ItemDisplay.class,e->{e.setItemStack(item);e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);e.setPersistent(false);e.setGravity(false);e.setInterpolationDelay(0);e.setInterpolationDuration(2);e.setTeleportDuration(2);});
            display.getPersistentDataContainer().set(plugin.instanceKey(),PersistentDataType.STRING,instanceId.toString());
            parts.put(bone.id,display);
        }
        return parts;
    }
    public void update(Location location,Map<String,ItemDisplay> parts,Map<String,Matrix4f> matrices,int duration){
        Location displayLocation=displayLocation(location);
        for(var e:parts.entrySet()){ItemDisplay display=e.getValue();if(!display.isValid())continue;
            Matrix4f transform=matrices.get(e.getKey());if(transform==null)continue;
            Location previousLocation=display.getLocation();
            if(previousLocation.distanceSquared(displayLocation)>0.0001||Math.abs(previousLocation.getYaw())>0.01||Math.abs(previousLocation.getPitch())>0.01){display.setTeleportDuration(duration);display.teleport(displayLocation);}
            Matrix4f previous=lastTransforms.get(display.getUniqueId());
            if(previous==null||!transform.equals(previous,0.00001f)){display.setInterpolationDelay(0);display.setInterpolationDuration(duration);display.setTransformationMatrix(transform);lastTransforms.computeIfAbsent(display.getUniqueId(),ignored->new Matrix4f()).set(transform);}
        }
    }
    public static Location displayLocation(Location controllerLocation){Location result=controllerLocation.clone();result.setYaw(0);result.setPitch(0);return result;}
    public void remove(Map<String,ItemDisplay> parts){for(ItemDisplay d:parts.values()){lastTransforms.remove(d.getUniqueId());if(d.isValid())d.remove();}parts.clear();}
}
