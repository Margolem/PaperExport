package dev.paperexport;

import dev.paperexport.format.PaperEntityDefinition;
import dev.paperexport.rig.PaperAnimationController;
import dev.paperexport.rig.PaperModelRenderer;
import dev.paperexport.rig.PaperMovementTracker;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Interaction;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.persistence.PersistentDataType;
import java.util.Map;
import java.util.UUID;

public final class PaperEntitySpawner {
    public static final class Instance {
        public final Entity controller;
        public final PaperEntityDefinition definition;
        public final UUID id;
        public final Map<String,org.bukkit.entity.ItemDisplay> parts;
        public final Map<String,Interaction> hitboxes=new java.util.HashMap<>();
        public final PaperAnimationController animation;
        public final PaperMovementTracker movement=new PaperMovementTracker();
        public boolean dying;
        public double deathTime;
        public Location lastLocation;
        public float lastYaw;
        public BossBar bossBar;
        public int nextBossPhase;
        public double ambientTime;
        public float headYaw,headPitch;
        Instance(Entity controller,PaperEntityDefinition definition,UUID id,Map<String,org.bukkit.entity.ItemDisplay> parts){
            this.controller=controller;this.definition=definition;this.id=id;this.parts=parts;this.animation=new PaperAnimationController(definition);this.lastLocation=controller.getLocation();this.lastYaw=controllerYaw(controller,lastLocation);
            var boss=definition.entity.boss;
            if(boss!=null&&boss.enabled)bossBar=Bukkit.createBossBar(boss.title.isBlank()?definition.manifest.name:boss.title,BarColor.valueOf(boss.bar_color),BarStyle.valueOf(boss.bar_style));
        }
    }
    private final PaperExportPlugin plugin;private final PaperModelRenderer renderer;
    public PaperEntitySpawner(PaperExportPlugin plugin,PaperModelRenderer renderer){this.plugin=plugin;this.renderer=renderer;}
    public Instance spawn(Location location,PaperEntityDefinition definition){
        EntityType type=EntityType.valueOf(definition.entity.base_entity);
        Entity entity=location.getWorld().spawnEntity(location,type);
        entity.setInvisible(true);entity.setSilent(definition.entity.stats.silent);entity.setInvulnerable(definition.entity.stats.invulnerable);entity.setGravity(definition.entity.stats.gravity);entity.setPersistent(definition.entity.stats.persistent);
        if(entity instanceof LivingEntity living){
            if(definition.entity.stats.persistent)living.setRemoveWhenFarAway(false);
            Attribute health=attribute("MAX_HEALTH","GENERIC_MAX_HEALTH");
            set(living,health,definition.entity.stats.max_health);living.setHealth(Math.min(definition.entity.stats.max_health,living.getAttribute(health).getValue()));
            set(living,attribute("MOVEMENT_SPEED","GENERIC_MOVEMENT_SPEED"),definition.entity.stats.movement_speed);
            set(living,attribute("ATTACK_DAMAGE","GENERIC_ATTACK_DAMAGE"),definition.entity.stats.attack_damage);
            set(living,attribute("FOLLOW_RANGE","GENERIC_FOLLOW_RANGE"),definition.entity.stats.follow_range);
            set(living,attribute("KNOCKBACK_RESISTANCE","GENERIC_KNOCKBACK_RESISTANCE"),definition.entity.stats.knockback_resistance);
            set(living,attribute("ARMOR","GENERIC_ARMOR"),definition.entity.stats.armor);
            set(living,attribute("ARMOR_TOUGHNESS","GENERIC_ARMOR_TOUGHNESS"),definition.entity.stats.armor_toughness);
            set(living,attribute("SCALE","GENERIC_SCALE"),definition.entity.stats.scale);
            EntityEquipment equipment=living.getEquipment();if(equipment!=null){equipment.setHelmet(null);equipment.setChestplate(null);equipment.setLeggings(null);equipment.setBoots(null);equipment.setItemInMainHand(null);equipment.setItemInOffHand(null);}
            if(entity instanceof Mob mob&&"stationary".equals(definition.entity.behavior))mob.setAI(false);
        }
        UUID id=UUID.randomUUID();entity.getPersistentDataContainer().set(plugin.entityKey(),PersistentDataType.STRING,definition.manifest.id);entity.getPersistentDataContainer().set(plugin.instanceKey(),PersistentDataType.STRING,id.toString());
        return new Instance(entity,definition,id,renderer.spawn(entity,definition,id));
    }
    public Instance restore(Entity controller,PaperEntityDefinition definition){
        String raw=controller.getPersistentDataContainer().get(plugin.instanceKey(),PersistentDataType.STRING);UUID id;
        try{id=raw==null?UUID.randomUUID():UUID.fromString(raw);}catch(IllegalArgumentException e){id=UUID.randomUUID();}
        controller.getPersistentDataContainer().set(plugin.instanceKey(),PersistentDataType.STRING,id.toString());
        return new Instance(controller,definition,id,renderer.spawn(controller,definition,id));
    }
    private static void set(LivingEntity entity,Attribute attribute,double value){AttributeInstance instance=entity.getAttribute(attribute);if(instance!=null)instance.setBaseValue(value);}
    static float controllerYaw(Entity controller,Location location){
        if(controller instanceof Mob mob&&!mob.hasAI())return location.getYaw();
        return controller instanceof LivingEntity living?living.getBodyYaw():location.getYaw();
    }
    private static Attribute attribute(String modern,String legacy){
        for(String name:new String[]{modern,legacy})try{return (Attribute)Attribute.class.getField(name).get(null);}
        catch(ReflectiveOperationException ignored){}
        throw new IllegalStateException("Missing Paper attribute "+modern);
    }
}
