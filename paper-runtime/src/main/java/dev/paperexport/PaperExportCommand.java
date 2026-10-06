package dev.paperexport;

import dev.paperexport.format.PaperEntityDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PaperExportCommand implements CommandExecutor,TabCompleter {
    private final PaperExportPlugin plugin;
    public PaperExportCommand(PaperExportPlugin plugin){this.plugin=plugin;}
    private void say(CommandSender sender,String message){sender.sendMessage(Component.text(message,NamedTextColor.AQUA));}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(args.length==0||"help".equalsIgnoreCase(args[0])){say(sender,"/pe list | info <id> | spawn <id> [amount] | kill <id> | reload | validate | pack | debug");return true;}
        String sub=args[0].toLowerCase(java.util.Locale.ROOT);
        boolean allowed=switch(sub){case "spawn"->sender.hasPermission("paperexport.spawn");case "reload","validate","pack"->sender.hasPermission("paperexport.reload");case "debug"->sender.hasPermission("paperexport.debug");default->sender.hasPermission("paperexport.admin");};
        if(!allowed){say(sender,"Missing permission.");return true;}
        switch(sub){
            case "list"->{say(sender,"PaperExport entities ("+plugin.registry().all().size()+"):");for(PaperEntityDefinition d:plugin.registry().all())say(sender,d.manifest.id+" · "+d.entity.stats.max_health+" HP · "+d.entity.behavior+" · "+d.model.bones.size()+" bones");}
            case "info"->{if(args.length<2){say(sender,"Usage: /pe info <id>");break;}PaperEntityDefinition d=plugin.registry().get(args[1]);say(sender,d==null?"Unknown entity: "+args[1]:d.manifest.name+" ("+d.manifest.id+") by "+d.manifest.author+" — "+d.manifest.description);}
            case "spawn"->{if(!sender.hasPermission("paperexport.spawn")){say(sender,"Missing paperexport.spawn permission");break;}if(!(sender instanceof Player player)){say(sender,"Spawn requires a player location");break;}if(args.length<2){say(sender,"Usage: /pe spawn <id> [amount]");break;}PaperEntityDefinition d=plugin.registry().get(args[1]);if(d==null){say(sender,"Unknown entity: "+args[1]);break;}int amount=1;try{if(args.length>2)amount=Integer.parseInt(args[2]);}catch(NumberFormatException ex){say(sender,"Amount must be 1–32");break;}if(amount<1||amount>32){say(sender,"Amount must be 1–32");break;}for(int n=0;n<amount;n++)plugin.api().spawn(d.manifest.id,player.getLocation());say(sender,"Spawned "+amount+" × "+d.manifest.id);}
            case "kill"->{if(args.length<2){say(sender,"Usage: /pe kill <id>");break;}int killed=0;for(var world:plugin.getServer().getWorlds())for(var entity:world.getEntities()){String id=entity.getPersistentDataContainer().get(plugin.entityKey(),org.bukkit.persistence.PersistentDataType.STRING);if(args[1].equals(id)){plugin.ticker().remove(entity);entity.remove();killed++;}}say(sender,"Removed "+killed+" instances of "+args[1]);}
            case "reload"->say(sender,plugin.reloadDefinitions());
            case "validate"->say(sender,plugin.validateFiles());
            case "pack"->say(sender,plugin.rebuildPack());
            case "debug"->{if(!sender.hasPermission("paperexport.debug")){say(sender,"Missing paperexport.debug permission");break;}say(sender,"Active: "+plugin.ticker().activeCount()+" · Displays: "+plugin.ticker().displayCount()+" · Mean tick: "+String.format(java.util.Locale.ROOT,"%.3f",plugin.ticker().averageMillis())+" ms");}
            default->say(sender,"Unknown subcommand. Run /pe help");
        }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(args.length==1)return Arrays.stream(new String[]{"help","list","info","spawn","kill","reload","validate","pack","debug"}).filter(s->s.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))).toList();
        if(args.length==2&&List.of("info","spawn","kill").contains(args[0].toLowerCase(java.util.Locale.ROOT)))return plugin.registry().all().stream().map(d->d.manifest.id).filter(s->s.startsWith(args[1])).toList();
        return List.of();
    }
}
