package dev.paperexport;

import dev.paperexport.rig.PaperModelRenderer;
import dev.paperexport.rig.RigMath;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PaperExportTicker implements Runnable {
    private final PaperModelRenderer renderer;private final PaperHitboxManager hitboxes;private final PaperEventDispatcher events;private final Map<UUID,PaperEntitySpawner.Instance> active=new HashMap<>();private final int updateTicks;private final double farDistanceSq;
    private long lastNanos,totalNanos,updates;
    public PaperExportTicker(PaperModelRenderer renderer,PaperHitboxManager hitboxes,PaperEventDispatcher events,int updateTicks,double farDistance){this.renderer=renderer;this.hitboxes=hitboxes;this.events=events;this.updateTicks=updateTicks;this.farDistanceSq=farDistance*farDistance;}
    public void add(PaperEntitySpawner.Instance instance){PaperEntitySpawner.Instance old=active.put(instance.controller.getUniqueId(),instance);if(old!=null){renderer.remove(old.parts);hitboxes.remove(old);}hitboxes.spawn(instance);}
    public PaperEntitySpawner.Instance get(Entity controller){return active.get(controller.getUniqueId());}
    public int activeCount(){return active.size();}
    public int displayCount(){return active.values().stream().mapToInt(i->i.parts.size()).sum();}
    public double averageMillis(){return updates==0?0:(double)totalNanos/updates/1_000_000;}
    public void remove(Entity controller){PaperEntitySpawner.Instance instance=active.remove(controller.getUniqueId());if(instance!=null){renderer.remove(instance.parts);hitboxes.remove(instance);if(instance.bossBar!=null)instance.bossBar.removeAll();}}
    public void stop(){for(var i:new ArrayList<>(active.values())){renderer.remove(i.parts);hitboxes.remove(i);if(i.bossBar!=null)i.bossBar.removeAll();}active.clear();}
    public void death(Entity controller){PaperEntitySpawner.Instance i=get(controller);if(i!=null){i.dying=true;i.deathTime=0;i.animation.play("death");if(i.bossBar!=null)i.bossBar.removeAll();}}
    @Override public void run(){long start=System.nanoTime();double dt=updateTicks/20.0;
        for(var instance:new ArrayList<>(active.values())){
            Entity controller=instance.controller;
            if(!instance.dying&&!controller.isValid()){remove(controller);continue;}
            if(instance.dying){instance.deathTime+=dt;var death=instance.definition.animations.get("death");if(death==null||instance.deathTime>death.length){remove(controller);continue;}}
            if(!instance.dying){instance.lastLocation=controller.getLocation();instance.lastYaw=instance.lastLocation.getYaw();}
            Location location=instance.lastLocation;
            if(!instance.dying)updateBoss(instance);
            boolean nearby=instance.dying||nearestPlayerSq(location)<=farDistanceSq;
            if(nearby&&!instance.dying){instance.ambientTime+=dt;if(instance.ambientTime>=8){instance.ambientTime=0;events.playCue(instance,"ambient");}}
            boolean moving=!instance.dying&&controller.getVelocity().lengthSquared()>0.003;
            if(nearby)for(var event:instance.animation.advance(dt,moving))events.fire(instance,event);
            Map<String,dev.paperexport.rig.PaperAnimationController.Pose> poses=new HashMap<>();for(var bone:instance.definition.model.bones)poses.put(bone.id,instance.animation.pose(bone));
            applyHeadTracking(instance,poses,dt);
            Map<String,Matrix4f> matrices=RigMath.worldMatrices(instance.definition.model.bones,poses,RigMath.rootYaw(instance.lastYaw), (float)instance.definition.entity.stats.scale);
            hitboxes.update(instance,matrices);
            if(nearby)renderer.update(location,instance.parts,matrices,updateTicks);
        }
        lastNanos=System.nanoTime()-start;totalNanos+=lastNanos;updates++;
    }
    private void updateBoss(PaperEntitySpawner.Instance instance){
        var boss=instance.definition.entity.boss;if(boss==null||!boss.enabled)return;
        double health=instance.controller instanceof LivingEntity living?Math.max(0,living.getHealth()):instance.definition.entity.stats.max_health;
        double ratio=Math.max(0,Math.min(1,health/instance.definition.entity.stats.max_health));
        instance.bossBar.setProgress(ratio);
        double rangeSquared=boss.range*boss.range;
        for(Player player:instance.lastLocation.getWorld().getPlayers()){
            boolean nearby=player.getLocation().distanceSquared(instance.lastLocation)<=rangeSquared;
            if(nearby&&!instance.bossBar.getPlayers().contains(player))instance.bossBar.addPlayer(player);
            else if(!nearby&&instance.bossBar.getPlayers().contains(player))instance.bossBar.removePlayer(player);
        }
        while(instance.nextBossPhase<boss.phases.size()&&ratio<=boss.phases.get(instance.nextBossPhase).below_health){
            var phase=boss.phases.get(instance.nextBossPhase++);
            if(phase.animation!=null)instance.animation.force(phase.animation);
            if(phase.sound!=null)events.playSound(instance,phase.sound);
        }
    }
    private static double nearestPlayerSq(Location location){double best=Double.MAX_VALUE;for(Player p:location.getWorld().getPlayers())best=Math.min(best,p.getLocation().distanceSquared(location));return best;}
    private void applyHeadTracking(PaperEntitySpawner.Instance instance,Map<String,dev.paperexport.rig.PaperAnimationController.Pose> poses,double dt){
        if(instance.dying)return;
        var head=instance.definition.model.bones.stream().filter(b->"head".equalsIgnoreCase(b.id)||"head".equalsIgnoreCase(b.name)).findFirst().orElse(null);
        if(head==null)return;
        LivingEntity target=instance.controller instanceof org.bukkit.entity.Mob mob?mob.getTarget():null;
        if(target==null){double closest=12*12;for(Player player:instance.lastLocation.getWorld().getPlayers())if(!player.isDead()){
            double distance=player.getLocation().distanceSquared(instance.lastLocation);if(distance<closest){closest=distance;target=player;}
        }}
        double wantedYaw=0,wantedPitch=0;
        if(target!=null){
            Location from=instance.controller instanceof LivingEntity living?living.getEyeLocation():instance.lastLocation;
            var delta=target.getEyeLocation().toVector().subtract(from.toVector());
            double targetYaw=Math.toDegrees(Math.atan2(-delta.getX(),delta.getZ()));
            wantedYaw=-Math.max(-65,Math.min(65,Math.IEEEremainder(targetYaw-instance.lastYaw,360)));
            wantedPitch=Math.max(-40,Math.min(40,Math.toDegrees(Math.atan2(delta.getY(),Math.hypot(delta.getX(),delta.getZ())))));
        }
        float alpha=(float)Math.min(1,dt*5);
        instance.headYaw+=(wantedYaw-instance.headYaw)*alpha;instance.headPitch+=(wantedPitch-instance.headPitch)*alpha;
        var pose=poses.get(head.id);if(pose!=null){
            var rotation=new org.joml.Quaternionf(pose.rotation()).rotateY((float)Math.toRadians(instance.headYaw)).rotateX((float)Math.toRadians(instance.headPitch));
            poses.put(head.id,new dev.paperexport.rig.PaperAnimationController.Pose(pose.position(),rotation,pose.scale()));
        }
    }
}
