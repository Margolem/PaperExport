package dev.paperexport;

import dev.paperexport.format.PaperEntityDefinition;
import dev.paperexport.format.PaperEntityLoader;
import dev.paperexport.api.PaperExportApi;
import dev.paperexport.api.PaperExportHitboxEvent;
import dev.paperexport.rig.PaperModelRenderer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class PaperExportPlugin extends JavaPlugin implements Listener {
    private final PaperEntityRegistry registry=new PaperEntityRegistry();
    private final PaperEntityLoader loader=new PaperEntityLoader();
    private final PaperResourcePackBuilder packBuilder=new PaperResourcePackBuilder();
    private NamespacedKey entityKey,instanceKey,hitboxKey;private PaperVersion version;private PaperModelRenderer renderer;private PaperHitboxManager hitboxes;private PaperEntitySpawner spawner;private PaperExportTicker ticker;private PaperResourcePackBuilder.Result pack;private PaperEventDispatcher events;private PaperExportApi api;
    public NamespacedKey entityKey(){return entityKey;}public NamespacedKey instanceKey(){return instanceKey;}public NamespacedKey hitboxKey(){return hitboxKey;}
    public PaperEntityRegistry registry(){return registry;}public PaperEntitySpawner spawner(){return spawner;}public PaperExportTicker ticker(){return ticker;}public PaperResourcePackBuilder.Result pack(){return pack;}
    public PaperVersion version(){return version;}
    public PaperEventDispatcher events(){return events;}public PaperExportApi api(){return api;}
    public boolean playSound(PaperEntitySpawner.Instance instance,String name){return events.playSound(instance,name);}
    public void fireCue(PaperEntitySpawner.Instance instance,String cue){events.playCue(instance,cue);}
    @Override public void onEnable(){
        try{version=PaperVersion.of(Bukkit.getMinecraftVersion());}
        catch(IllegalArgumentException error){getLogger().severe(error.getMessage());getServer().getPluginManager().disablePlugin(this);return;}
        getLogger().info("PaperExport target: Paper "+version.minecraft()+" (resource pack "+version.packMajor()+"."+version.packMinor()+")");
        saveDefaultConfig();for(String name:new String[]{"entities","resourcepack","cache","generated","logs"})try{Files.createDirectories(getDataFolder().toPath().resolve(name));}catch(IOException e){getLogger().severe("Cannot create "+name+": "+e.getMessage());getServer().getPluginManager().disablePlugin(this);return;}
        entityKey=new NamespacedKey(this,"entity_id");instanceKey=new NamespacedKey(this,"instance_id");hitboxKey=new NamespacedKey(this,"hitbox_id");renderer=new PaperModelRenderer(this);hitboxes=new PaperHitboxManager(this);spawner=new PaperEntitySpawner(this,renderer);
        int ticks=Math.max(1,Math.min(20,getConfig().getInt("animation.update-ticks",2)));double far=Math.max(16,Math.min(256,getConfig().getDouble("animation.far-distance",48)));
        events=new PaperEventDispatcher(this);ticker=new PaperExportTicker(renderer,hitboxes,events,ticks,far);Bukkit.getScheduler().runTaskTimer(this,ticker,1,ticks);
        api=new PaperExportApiImpl(this);Bukkit.getServicesManager().register(PaperExportApi.class,api,this,ServicePriority.Normal);
        Bukkit.getPluginManager().registerEvents(this,this);
        var command=getCommand("paperexport");if(command!=null){PaperExportCommand executor=new PaperExportCommand(this);command.setExecutor(executor);command.setTabCompleter(executor);}
        reloadDefinitions();Bukkit.getScheduler().runTask(this,()->{for(World w:Bukkit.getWorlds())for(Chunk c:w.getLoadedChunks())recover(c);});
    }
    @Override public void onDisable(){Bukkit.getServicesManager().unregisterAll(this);if(ticker!=null)ticker.stop();}
    public synchronized String reloadDefinitions(){
        reloadConfig();Path folder=getDataFolder().toPath().resolve("entities");Map<String,PaperEntityDefinition> next=new HashMap<>();Set<String> paths=new HashSet<>();int failed=0;
        try(var stream=Files.list(folder)){for(Path file:stream.filter(p->p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".paperexport")).sorted().toList()){
            try{PaperEntityDefinition d=loader.load(file);if(next.containsKey(d.manifest.id))throw new IOException("Duplicate entity ID "+d.manifest.id);
                for(String path:d.files.keySet())if(path.startsWith("resourcepack/assets/")&&!path.endsWith("/sounds.json")){if(paths.contains(path))throw new IOException("Resource-pack conflict: "+path);}
                next.put(d.manifest.id,d);for(String path:d.files.keySet())if(path.startsWith("resourcepack/assets/")&&!path.endsWith("/sounds.json"))paths.add(path);
                getLogger().info("Loaded "+d.manifest.id);
            }catch(Exception ex){failed++;getLogger().warning("Skipped "+file.getFileName()+": "+ex.getMessage());}
        }}catch(IOException ex){return "Scan failed: "+ex.getMessage();}
        try{PaperResourcePackBuilder.Result built=packBuilder.build(next.values(),getDataFolder().toPath().resolve("generated/resourcepack.zip"),version);registry.replace(next);pack=built;
            String message="Registered "+next.size()+" entities; pack has "+built.resources()+" assets (SHA-1 "+built.sha1()+"); skipped "+failed+" packages";
            getLogger().info(message);if(!next.isEmpty())getLogger().info("Existing instances retain old definitions after reload; respawn them to apply model changes.");for(var player:Bukkit.getOnlinePlayers())sendPack(player);return message;
        }catch(IOException ex){getLogger().severe("Pack build failed; retaining previous registry: "+ex.getMessage());return "Pack build failed: "+ex.getMessage();}
    }
    public String validateFiles(){Path folder=getDataFolder().toPath().resolve("entities");int valid=0,invalid=0;StringBuilder problems=new StringBuilder();
        try(var stream=Files.list(folder)){for(Path file:stream.filter(p->p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".paperexport")).toList())try{loader.load(file);valid++;}catch(Exception e){invalid++;problems.append("; ").append(file.getFileName()).append(": ").append(e.getMessage());}}
        catch(IOException e){return "Validation scan failed: "+e.getMessage();}return "Valid packages: "+valid+"; invalid: "+invalid+problems;
    }
    public String rebuildPack(){try{pack=packBuilder.build(registry.all(),getDataFolder().toPath().resolve("generated/resourcepack.zip"),version);for(var player:Bukkit.getOnlinePlayers())sendPack(player);return "Pack rebuilt: "+pack.path()+" · SHA-1 "+pack.sha1()+" · "+pack.resources()+" assets";}catch(IOException e){return "Pack build failed: "+e.getMessage();}}
    private void recover(Chunk chunk){
        for(Entity entity:chunk.getEntities()){
            String id=entity.getPersistentDataContainer().get(entityKey,PersistentDataType.STRING);if(id==null||ticker.get(entity)!=null)continue;
            PaperEntityDefinition d=registry.get(id);if(d==null){getLogger().warning("Loaded custom entity "+id+" without an installed definition");continue;}
            ticker.add(spawner.restore(entity,d));
        }
    }
    @EventHandler public void onChunkLoad(ChunkLoadEvent event){Bukkit.getScheduler().runTask(this,()->recover(event.getChunk()));}
    @EventHandler public void onChunkUnload(ChunkUnloadEvent event){for(Entity e:event.getChunk().getEntities())if(e.getPersistentDataContainer().has(entityKey,PersistentDataType.STRING))ticker.remove(e);}
    @EventHandler public void onDeath(EntityDeathEvent event){var i=ticker.get(event.getEntity());if(i!=null)fireCue(i,"death");ticker.death(event.getEntity());}
    @EventHandler public void onDamage(EntityDamageEvent event){var i=ticker.get(event.getEntity());if(i!=null&&!i.dying){i.animation.play("hurt");fireCue(i,"hurt");}}
    @EventHandler public void onAttack(EntityDamageByEntityEvent event){var i=ticker.get(event.getDamager());if(i!=null&&!i.dying){i.animation.play("attack");fireCue(i,"attack");}}
    @EventHandler public void onHitboxAttack(PrePlayerAttackEntityEvent event){
        var hit=hitboxes.owner(event.getAttacked());if(hit==null||hit.instance().dying)return;
        if(event.isCancelled()||!event.willAttack())return;
        event.setCancelled(true);
        var custom=new PaperExportHitboxEvent(hit.instance().controller,event.getPlayer(),hit.regionId(),PaperExportHitboxEvent.Action.ATTACK);
        Bukkit.getPluginManager().callEvent(custom);
        if(!custom.isCancelled()&&hit.instance().controller instanceof org.bukkit.entity.LivingEntity living&&living.isValid())event.getPlayer().attack(living);
    }
    @EventHandler public void onHitboxInteract(PlayerInteractAtEntityEvent event){
        var hit=hitboxes.owner(event.getRightClicked());if(hit==null||hit.instance().dying)return;
        event.setCancelled(true);
        Bukkit.getPluginManager().callEvent(new PaperExportHitboxEvent(hit.instance().controller,event.getPlayer(),hit.regionId(),PaperExportHitboxEvent.Action.INTERACT));
    }
    @EventHandler public void onPluginDisable(PluginDisableEvent event){if(events!=null)events.unregister(event.getPlugin());}
    @EventHandler public void onJoin(PlayerJoinEvent event){Bukkit.getScheduler().runTaskLater(this,()->sendPack(event.getPlayer()),20);}
    public void sendPack(org.bukkit.entity.Player player){if(pack==null)return;String mode=getConfig().getString("resource-pack.mode","external");if("disabled".equals(mode))return;
        if(!"external".equals(mode)){getLogger().warning("resource-pack.mode="+mode+" is unavailable; use external HTTPS or disabled");return;}
        String url=getConfig().getString("resource-pack.url","");if(url==null||url.isBlank())return;
        if(!url.startsWith("https://")){getLogger().warning("Resource-pack URL must use HTTPS: "+url);return;}
        player.setResourcePack(url,pack.sha1(),getConfig().getBoolean("resource-pack.required",false),Component.text("PaperExport entity models"));
    }
}
