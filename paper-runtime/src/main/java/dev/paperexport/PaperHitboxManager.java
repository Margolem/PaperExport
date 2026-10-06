package dev.paperexport;

import dev.paperexport.format.PaperEntityDefinition.HitboxRegion;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/** One non-persistent Interaction entity per exported pe_hitbox cube. */
final class PaperHitboxManager {
    record Hit(PaperEntitySpawner.Instance instance,String regionId) {}
    private final PaperExportPlugin plugin;
    private final Map<java.util.UUID,Hit> owners=new HashMap<>();
    PaperHitboxManager(PaperExportPlugin plugin){this.plugin=plugin;}

    void spawn(PaperEntitySpawner.Instance instance){
        if(instance.definition.model.hitboxes==null)return;
        for(HitboxRegion region:instance.definition.model.hitboxes){
            Interaction hitbox=instance.controller.getWorld().spawn(instance.controller.getLocation(),Interaction.class,e->{
                e.setPersistent(false);e.setGravity(false);e.setResponsive(true);
                e.setInteractionWidth((float)Math.max(region.to[0]-region.from[0],region.to[2]-region.from[2])/16);
                e.setInteractionHeight((float)(region.to[1]-region.from[1])/16);
            });
            hitbox.getPersistentDataContainer().set(plugin.instanceKey(),PersistentDataType.STRING,instance.id.toString());
            hitbox.getPersistentDataContainer().set(plugin.hitboxKey(),PersistentDataType.STRING,region.id);
            instance.hitboxes.put(region.id,hitbox);
            owners.put(hitbox.getUniqueId(),new Hit(instance,region.id));
        }
    }
    Hit owner(Entity entity){return owners.get(entity.getUniqueId());}
    void remove(PaperEntitySpawner.Instance instance){
        for(Interaction entity:instance.hitboxes.values()){owners.remove(entity.getUniqueId());if(entity.isValid())entity.remove();}
        instance.hitboxes.clear();
    }
    void update(PaperEntitySpawner.Instance instance,Map<String,Matrix4f> matrices){
        if(instance.definition.model.hitboxes==null)return;
        for(HitboxRegion region:instance.definition.model.hitboxes){
            Interaction entity=instance.hitboxes.get(region.id);Matrix4f matrix=matrices.get(region.bone);
            if(entity==null||!entity.isValid()||matrix==null)continue;
            var bone=instance.definition.model.bones.stream().filter(b->b.id.equals(region.bone)).findFirst().orElse(null);
            if(bone==null)continue;
            float minX=Float.POSITIVE_INFINITY,minY=Float.POSITIVE_INFINITY,minZ=Float.POSITIVE_INFINITY;
            float maxX=Float.NEGATIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY,maxZ=Float.NEGATIVE_INFINITY;
            for(int x=0;x<2;x++)for(int y=0;y<2;y++)for(int z=0;z<2;z++){
                Vector3f point=new Vector3f((float)((region.from[0]*(1-x)+region.to[0]*x-bone.pivot[0])/16),
                    (float)((region.from[1]*(1-y)+region.to[1]*y-bone.pivot[1])/16),
                    (float)((region.from[2]*(1-z)+region.to[2]*z-bone.pivot[2])/16));
                matrix.transformPosition(point);
                minX=Math.min(minX,point.x);maxX=Math.max(maxX,point.x);
                minY=Math.min(minY,point.y);maxY=Math.max(maxY,point.y);
                minZ=Math.min(minZ,point.z);maxZ=Math.max(maxZ,point.z);
            }
            entity.setInteractionWidth(Math.max(.05f,Math.max(maxX-minX,maxZ-minZ)));
            entity.setInteractionHeight(Math.max(.05f,maxY-minY));
            Location origin=instance.lastLocation;
            Location target=origin.clone().add((minX+maxX)/2,minY,(minZ+maxZ)/2);
            if(entity.getLocation().distanceSquared(target)>0.0001)entity.teleport(target);
        }
    }
}
