package dev.underthebedrock.team;

import dev.underthebedrock.UnderTheBedrockPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import java.io.File;
import java.io.IOException;
import java.util.*;

public final class TeamManager implements Listener, CommandExecutor, TabCompleter {
    private final UnderTheBedrockPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    public TeamManager(UnderTheBedrockPlugin plugin) {
        this.plugin=plugin; file=new File(plugin.getDataFolder(),"teams.yml");
        data=YamlConfiguration.loadConfiguration(file);
    }
    public void start(){ plugin.getServer().getPluginManager().registerEvents(this,plugin); }
    public void stop(){ save(); }
    private void save(){ try { data.save(file); } catch(IOException e){ plugin.getLogger().severe("Could not save teams.yml: "+e.getMessage()); } }
    private String teamPath(UUID id){ return "teams."+id; }
    public UUID teamOf(UUID player){
        ConfigurationSection all=data.getConfigurationSection("teams");if(all==null)return null;
        for(String leader:all.getKeys(false)){
            String p=teamPath(UUID.fromString(leader));
            if(leader.equals(player.toString())||data.getStringList(p+".members").contains(player.toString())) return UUID.fromString(leader);
        }
        return null;
    }
    public boolean sameTeam(UUID a,UUID b){ UUID x=teamOf(a); return x!=null&&x.equals(teamOf(b)); }
    public boolean isMember(UUID leader,UUID player){ return leader.equals(player)||data.getStringList(teamPath(leader)+".members").contains(player.toString()); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void friendlyFire(EntityDamageByEntityEvent event){
        if(!(event.getEntity() instanceof Player victim))return;
        Player attacker=null; Entity damager=event.getDamager();
        if(damager instanceof Player p) attacker=p;
        else if(damager instanceof Projectile projectile && projectile.getShooter() instanceof Player p) attacker=p;
        if(attacker!=null && sameTeam(attacker.getUniqueId(),victim.getUniqueId())) event.setCancelled(true);
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player player)){sender.sendMessage("Players only.");return true;}
        UUID id=player.getUniqueId(); UUID leader=teamOf(id);
        if(args.length==0){ show(player,leader); return true; }
        switch(args[0].toLowerCase(Locale.ROOT)){
            case "create" -> {
                if(leader!=null){player.sendMessage(ChatColor.RED+"Leave your current team first.");return true;}
                if(args.length<2){player.sendMessage(ChatColor.YELLOW+"Usage: /team create <name>");return true;}
                String name=String.join(" ",Arrays.copyOfRange(args,1,args.length));
                if(name.length()>24){player.sendMessage(ChatColor.RED+"Team names are limited to 24 characters.");return true;}
                data.set(teamPath(id)+".name",name);data.set(teamPath(id)+".members",new ArrayList<String>());save();
                player.sendMessage(ChatColor.GREEN+"Team created: "+ChatColor.WHITE+name);
            }
            case "invite" -> {
                if(leader==null||!leader.equals(id)){player.sendMessage(ChatColor.RED+"Only a team owner can invite players.");return true;}
                if(args.length!=2){player.sendMessage(ChatColor.YELLOW+"Usage: /team invite <player>");return true;}
                Player target=Bukkit.getPlayerExact(args[1]);
                if(target==null||target.equals(player)){player.sendMessage(ChatColor.RED+"That player must be online and cannot be you.");return true;}
                if(teamOf(target.getUniqueId())!=null){player.sendMessage(ChatColor.RED+"That player is already in a team.");return true;}
                data.set("invites."+target.getUniqueId(),id.toString());save();
                target.sendMessage(ChatColor.GOLD+player.getName()+" invited you to team "+ChatColor.WHITE+data.getString(teamPath(id)+".name",player.getName())+ChatColor.GOLD+". Use /team accept.");
                player.sendMessage(ChatColor.GREEN+"Invitation sent to "+target.getName()+".");
            }
            case "accept" -> {
                String invited=data.getString("invites."+id);
                if(invited==null){player.sendMessage(ChatColor.RED+"You have no pending team invitation.");return true;}
                UUID owner=UUID.fromString(invited);
                if(!data.contains(teamPath(owner))){data.set("invites."+id,null);save();player.sendMessage(ChatColor.RED+"That team no longer exists.");return true;}
                if(teamOf(id)!=null){player.sendMessage(ChatColor.RED+"Leave your current team first.");return true;}
                List<String> members=new ArrayList<>(data.getStringList(teamPath(owner)+".members"));members.add(id.toString());
                data.set(teamPath(owner)+".members",members);data.set("invites."+id,null);save();
                player.sendMessage(ChatColor.GREEN+"You joined "+data.getString(teamPath(owner)+".name","the team")+".");
            }
            case "leave" -> {
                if(leader==null){player.sendMessage(ChatColor.RED+"You are not in a team.");return true;}
                if(leader.equals(id)){
                    List<String> members=data.getStringList(teamPath(id)+".members");
                    if(!members.isEmpty()){player.sendMessage(ChatColor.RED+"Transfer leadership or remove members before disbanding.");return true;}
                    data.set(teamPath(id),null);
                }else{
                    List<String> members=new ArrayList<>(data.getStringList(teamPath(leader)+".members"));members.remove(id.toString());data.set(teamPath(leader)+".members",members);
                }
                save();player.sendMessage(ChatColor.YELLOW+"You left the team.");
            }
            case "kick" -> {
                if(leader==null||!leader.equals(id)||args.length!=2){player.sendMessage(ChatColor.YELLOW+"Usage: /team kick <player> (team owner only)");return true;}
                OfflinePlayer target=Bukkit.getOfflinePlayer(args[1]);List<String> members=new ArrayList<>(data.getStringList(teamPath(id)+".members"));
                if(!members.remove(target.getUniqueId().toString())){player.sendMessage(ChatColor.RED+"That player is not on your team.");return true;}
                data.set(teamPath(id)+".members",members);save();player.sendMessage(ChatColor.GREEN+"Removed "+target.getName()+" from the team.");
                if(target.isOnline())target.getPlayer().sendMessage(ChatColor.RED+"You were removed from the team.");
            }
            default -> player.sendMessage(ChatColor.YELLOW+"/team <create|invite|accept|leave|kick>");
        }
        return true;
    }
    private void show(Player p,UUID leader){
        if(leader==null){p.sendMessage(ChatColor.GRAY+"You are not in a team. Use /team create <name>.");return;}
        String path=teamPath(leader);p.sendMessage(ChatColor.GOLD+"TEAM: "+ChatColor.WHITE+data.getString(path+".name","Unnamed"));
        p.sendMessage(ChatColor.GRAY+"Owner: "+Bukkit.getOfflinePlayer(leader).getName());
        for(String member:data.getStringList(path+".members"))p.sendMessage(ChatColor.GRAY+" • "+Bukkit.getOfflinePlayer(UUID.fromString(member)).getName());
        p.sendMessage(ChatColor.DARK_GRAY+"Friendly fire is disabled between teammates.");
    }
    @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){
        if(args.length==1)return List.of("create","invite","accept","leave","kick").stream().filter(x->x.startsWith(args[0].toLowerCase())).toList();
        if(args.length==2&&(args[0].equalsIgnoreCase("invite")||args[0].equalsIgnoreCase("kick")))return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n->n.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        return List.of();
    }
}
