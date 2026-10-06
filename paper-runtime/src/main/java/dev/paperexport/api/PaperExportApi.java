package dev.paperexport.api;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Main-thread integration API. Obtain it with
 * Bukkit.getServicesManager().load(PaperExportApi.class).
 * Consumer plugins must declare depend: [PaperExport] and compile against the
 * API JAR. The implementation is provided only by the installed PaperExport plugin.
 */
public interface PaperExportApi {
    record EntityInfo(String id, String name, double maxHealth, boolean boss,
                      Set<String> animations, Set<String> sounds) {}
    @FunctionalInterface interface CustomEventHandler {
        void handle(Entity controller, String eventId, Map<String,Object> data);
    }
    Collection<EntityInfo> entities();
    Entity spawn(String entityId, Location location);
    boolean playAnimation(Entity controller, String animation);
    boolean playSound(Entity controller, String soundName);
    String entityId(Entity controller);
    boolean despawn(Entity controller);
    String reloadPackages();
    void registerCustomEvent(Plugin owner, String eventId, CustomEventHandler handler);
    void unregisterCustomEvents(Plugin owner);
}
