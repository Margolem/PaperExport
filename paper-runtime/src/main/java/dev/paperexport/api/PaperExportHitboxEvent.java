package dev.paperexport.api;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired when a player attacks or interacts with an exported pe_hitbox region. */
public final class PaperExportHitboxEvent extends Event implements Cancellable {
    public enum Action { ATTACK, INTERACT }
    private static final HandlerList HANDLERS=new HandlerList();
    private final Entity controller;
    private final Player player;
    private final String regionId;
    private final Action action;
    private boolean cancelled;
    public PaperExportHitboxEvent(Entity controller,Player player,String regionId,Action action){
        this.controller=controller;this.player=player;this.regionId=regionId;this.action=action;
    }
    public Entity controller(){return controller;}
    public Player player(){return player;}
    public String regionId(){return regionId;}
    public Action action(){return action;}
    @Override public boolean isCancelled(){return cancelled;}
    @Override public void setCancelled(boolean cancelled){this.cancelled=cancelled;}
    @Override public @NotNull HandlerList getHandlers(){return HANDLERS;}
    public static @NotNull HandlerList getHandlerList(){return HANDLERS;}
}
