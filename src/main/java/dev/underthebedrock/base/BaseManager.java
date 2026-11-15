package dev.underthebedrock.base;

import dev.underthebedrock.UnderTheBedrockPlugin;
import dev.underthebedrock.item.CustomItemManager;
import dev.underthebedrock.team.TeamManager;
import dev.underthebedrock.world.VoidWorldManager;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import java.io.File;
import java.io.IOException;
import java.util.*;
import org.bukkit.scheduler.BukkitTask;

public final class BaseManager implements Listener, CommandExecutor, TabCompleter {
    private enum Kind {
        WORKSHOP("industrial_workshop","Industrial Workshop",1,0.68,1),
        RESEARCH("research_facility","Research Facility",1,1.00,4),
        BUNKER("underground_bunker","Underground Bunker",6,1.35,3),
        RAILWAY("railway_outpost","Railway Outpost",4,1.20,2);
        final String id,name; final int chests,security; final double forge;
        Kind(String id,String name,int chests,double forge,int security){
            this.id=id;this.name=name;this.chests=chests;this.forge=forge;this.security=security;
        }
    }
    private static final String[] PERMS={"enter","storage","forge","build","security","research"};
    private static final String[] RECIPES={"null_fragment","resonance_core","charm_of_the_forgotten","null_ward","power_cell"};
    private static final String[] OUTPUTS={"resonance_core","charm_of_the_forgotten","null_ward","power_cell","reconstructor"};
    private static final long[] HOURS={1,3,8,16,24};
    private final UnderTheBedrockPlugin plugin; private final VoidWorldManager worlds; private final TeamManager teams;
    private final File file,sitesFile; private final YamlConfiguration data,sites;
    private final NamespacedKey constructorKey,baseBlockKey;
    private final Map<UUID,String> selection=new HashMap<>();
    private final Map<UUID,Inventory> menus=new HashMap<>();
    private final Map<UUID,String> forgeBase=new HashMap<>();
    private final Map<String,Long> lastSecurityAlert=new HashMap<>();
    private BukkitTask securityTask;
    public BaseManager(UnderTheBedrockPlugin p,VoidWorldManager w,TeamManager t){
        plugin=p;worlds=w;teams=t;file=new File(p.getDataFolder(),"bases.yml");sitesFile=new File(p.getDataFolder(),"structures.yml");
        data=YamlConfiguration.loadConfiguration(file);sites=YamlConfiguration.loadConfiguration(sitesFile);
        constructorKey=new NamespacedKey(p,"base-constructor");baseBlockKey=new NamespacedKey(p,"player-base-block");
    }
    public void start(){plugin.getServer().getPluginManager().registerEvents(this,plugin);securityTask=plugin.getServer().getScheduler().runTaskTimer(plugin,this::securityTick,100L,100L);}
    public void stop(){if(securityTask!=null)securityTask.cancel();securityTask=null;save();}
    private void save(){try{data.save(file);}catch(IOException e){plugin.getLogger().severe("Could not save bases.yml: "+e.getMessage());}}
    public ItemStack constructorItem(){
        ItemStack item=new ItemStack(Material.LODESTONE);ItemMeta meta=item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD+"Base Constructor");
        meta.setLore(List.of(ChatColor.GRAY+"A sealed construction seed recovered from rare airdrops.",ChatColor.YELLOW+"Use or drop it inside an abandoned construction site.",ChatColor.DARK_GRAY+"Under The Bedrock"));
        meta.getPersistentDataContainer().set(constructorKey,PersistentDataType.BYTE,(byte)1);item.setItemMeta(meta);return item;
    }
    private boolean isConstructor(ItemStack i){return i!=null&&i.hasItemMeta()&&i.getItemMeta().getPersistentDataContainer().has(constructorKey,PersistentDataType.BYTE);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void interact(PlayerInteractEvent e){
        if(e.getHand()!=EquipmentSlot.HAND||!isConstructor(e.getItem()))return;
        if(e.getAction()!=Action.RIGHT_CLICK_AIR&&e.getAction()!=Action.RIGHT_CLICK_BLOCK)return;
        e.setCancelled(true);Player p=e.getPlayer();
        if(!worlds.isActive()||!p.getWorld().equals(worlds.getWorld())){p.sendMessage(ChatColor.RED+"The constructor only works beneath the bedrock.");return;}
        if(baseOf(p.getUniqueId())!=null){p.sendMessage(ChatColor.RED+"You already own a base.");return;}
        String site=nearConstructionSite(p.getLocation());
        if(site==null){p.sendMessage(ChatColor.GRAY+"The constructor needs an abandoned construction area nearby.");return;}
        openSelection(p,site);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void dropped(PlayerDropItemEvent e){
        if(!isConstructor(e.getItemDrop().getItemStack()))return;
        Player p=e.getPlayer();if(!worlds.isActive()||!p.getWorld().equals(worlds.getWorld()))return;
        String site=nearConstructionSite(p.getLocation());if(site==null)return;
        e.setCancelled(true);openSelection(p,site);
    }
    private String nearConstructionSite(Location at){
        YamlConfiguration current=YamlConfiguration.loadConfiguration(sitesFile);
        ConfigurationSection all=current.getConfigurationSection("sites");if(all==null)return null;
        String best=null;double bestDistance=32*32;
        for(String key:all.getKeys(false)){
            String type=current.getString("sites."+key+".type","");
            if(!Set.of("empty_settlement","research_facility","underground_bunker","railway").contains(type))continue;
            int x=current.getInt("sites."+key+".x"),z=current.getInt("sites."+key+".z");
            double d=Math.pow(at.getX()-x,2)+Math.pow(at.getZ()-z,2);
            if(d<bestDistance){bestDistance=d;best=key;}
        }
        return best;
    }
    private void openSelection(Player p,String site){
        selection.put(p.getUniqueId(),site);
        Inventory inv=Bukkit.createInventory(null,9,ChatColor.DARK_GRAY+"Choose your base");
        inv.setItem(1,choice(Kind.WORKSHOP,Material.BLAST_FURNACE,"1 chest · fastest forging · security I"));
        inv.setItem(3,choice(Kind.RESEARCH,Material.ENCHANTING_TABLE,"1 chest · standard forging · best security IV"));
        inv.setItem(5,choice(Kind.BUNKER,Material.OBSIDIAN,"6 chests · slow forging · security III"));
        inv.setItem(7,choice(Kind.RAILWAY,Material.RAIL,"4 chests · faster than bunker · security II"));
        p.openInventory(inv);
    }
    private ItemStack choice(Kind k,Material icon,String detail){
        ItemStack i=new ItemStack(icon);ItemMeta m=i.getItemMeta();m.setDisplayName(ChatColor.GOLD+k.name);
        m.setLore(List.of(ChatColor.GRAY+detail,ChatColor.DARK_GRAY+"Handcrafted abandoned-site conversion"));i.setItemMeta(m);return i;
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        String title=ChatColor.stripColor(e.getView().getTitle());
        if("Choose your base".equals(title)){
            e.setCancelled(true);Kind k=switch(e.getRawSlot()){case 1->Kind.WORKSHOP;case 3->Kind.RESEARCH;case 5->Kind.BUNKER;case 7->Kind.RAILWAY;default->null;};
            if(k!=null){String site=selection.remove(p.getUniqueId());p.closeInventory();if(site!=null)construct(p,site,k);}
            return;
        }
        if("Artifact Forge".equals(title)){
            e.setCancelled(true);String id=forgeBase.get(p.getUniqueId());if(id==null)return;
            int slot=e.getRawSlot();if(slot>=0&&slot<5)startRecipe(p,id,slot);
            if(slot==8)claim(p,id);
        }
    }
    private void construct(Player p,String site,Kind kind){
        if(baseOf(p.getUniqueId())!=null){p.sendMessage(ChatColor.RED+"You already own a base.");return;}
        YamlConfiguration currentSites=YamlConfiguration.loadConfiguration(sitesFile);
        int sx=currentSites.getInt("sites."+site+".x"),sz=currentSites.getInt("sites."+site+".z");
        World w=worlds.getWorld();int x=sx+27,z=sz;
        int y=plugin.getConfig().getInt("world.floor-y",64)+1;
        for(int cx=Math.floorDiv(x-9,16);cx<=Math.floorDiv(x+9,16);cx++)for(int cz=Math.floorDiv(z-6,16);cz<=Math.floorDiv(z+6,16);cz++)if(!w.isChunkLoaded(cx,cz)){p.sendMessage(ChatColor.RED+"The construction area is not fully loaded yet. Walk around it and try again.");return;}
        for(int dx=-9;dx<=9;dx++)for(int dz=-6;dz<=6;dz++)for(int dy=0;dy<=6;dy++)
            if(!w.getBlockAt(x+dx,y+dy,z+dz).getType().isAir()){p.sendMessage(ChatColor.RED+"The base footprint is obstructed. Clear the area first.");return;}
        String id=UUID.randomUUID().toString();buildBlueprint(w,x,y,z,kind,id);
        String path="bases."+id;data.set(path+".owner",p.getUniqueId().toString());data.set(path+".team",teams.teamOf(p.getUniqueId())==null?"":teams.teamOf(p.getUniqueId()).toString());
        data.set(path+".kind",kind.id);data.set(path+".world",w.getName());data.set(path+".x",x);data.set(path+".y",y);data.set(path+".z",z);
        data.set(path+".created",System.currentTimeMillis());data.set(path+".site",site);save();
        ItemStack held=p.getInventory().getItemInMainHand();if(isConstructor(held)){if(held.getAmount()==1)p.getInventory().setItemInMainHand(null);else held.setAmount(held.getAmount()-1);}
        p.sendTitle(ChatColor.GOLD+"BASE ESTABLISHED",ChatColor.GRAY+kind.name,10,60,15);
        p.sendMessage(ChatColor.GREEN+"Your "+kind.name+" is online. Use /base to manage it.");
    }
    private void buildBlueprint(World w,int x,int y,int z,Kind k,String id){
        Material wall=switch(k){case WORKSHOP->Material.DEEPSLATE_BRICKS;case RESEARCH->Material.POLISHED_ANDESITE;case BUNKER->Material.POLISHED_DEEPSLATE;case RAILWAY->Material.TUFF_BRICKS;};
        Material trim=switch(k){case WORKSHOP->Material.COPPER_BLOCK;case RESEARCH->Material.QUARTZ_BLOCK;case BUNKER->Material.IRON_BLOCK;case RAILWAY->Material.IRON_BLOCK;};
        for(int dx=-8;dx<=8;dx++)for(int dz=-5;dz<=5;dz++){
            w.getBlockAt(x+dx,y,z+dz).setType((Math.abs(dx)==8||Math.abs(dz)==5)?trim:Material.DEEPSLATE_TILES,false);
            for(int dy=1;dy<=4;dy++){
                boolean edge=Math.abs(dx)==8||Math.abs(dz)==5;
                if(edge){boolean window=(dy==2&&((dx==0&&Math.abs(dz)==5)||(dz==0&&Math.abs(dx)==8)));w.getBlockAt(x+dx,y+dy,z+dz).setType(window?Material.TINTED_GLASS:wall,false);}
                else if(dy==4)w.getBlockAt(x+dx,y+dy,z+dz).setType(Material.DEEPSLATE_TILES,false);
            }
        }
        // Deliberately irregular trim, patched walls and warm practical lighting keep it handcrafted.
        for(int[] c:new int[][]{{-7,1,-4},{7,1,-4},{-7,1,4},{7,1,4},{-3,1,-5},{4,1,5}})
            w.getBlockAt(x+c[0],y+c[1],z+c[2]).setType(Material.CRACKED_DEEPSLATE_BRICKS,false);
        w.getBlockAt(x,y+1,z-5).setType(Material.AIR,false);w.getBlockAt(x,y+2,z-5).setType(Material.AIR,false);
        w.getBlockAt(x,y+1,z-4).setType(Material.AIR,false);
        w.getBlockAt(x-6,y+1,z-4).setType(Material.REDSTONE_LAMP,false);
        w.getBlockAt(x-6,y+2,z-4).setType(Material.LIGHTNING_ROD,false);
        w.getBlockAt(x-5,y+1,z-4).setType(Material.COPPER_BLOCK,false);
        w.getBlockAt(x-5,y+1,z-2).setType(Material.CRAFTING_TABLE,false);
        w.getBlockAt(x-4,y+1,z-2).setType(Material.FURNACE,false);
        w.getBlockAt(x+4,y+1,z-2).setType(Material.SMITHING_TABLE,false);
        w.getBlockAt(x+5,y+1,z-2).setType(Material.BLAST_FURNACE,false);
        w.getBlockAt(x-5,y+1,z+3).setType(Material.RED_BED,false);
        w.getBlockAt(x+5,y+1,z+3).setType(Material.LANTERN,false);
        w.getBlockAt(x,y+1,z+4).setType(Material.LOOM,false);
        int[][] chestPositions={{-6,2},{-3,3},{0,3},{3,3},{6,2},{0,-1}};
        for(int n=0;n<k.chests;n++){int[] c=chestPositions[n];w.getBlockAt(x+c[0],y+1,z+c[1]).setType(Material.CHEST,false);mark(w.getBlockAt(x+c[0],y+1,z+c[1]),id);}
        Block forge=w.getBlockAt(x+4,y+1,z-2);mark(forge,id);
        Block security=w.getBlockAt(x-4,y+1,z+3);security.setType(Material.LODESTONE,false);mark(security,id);
        Block terminal=w.getBlockAt(x+4,y+1,z+3);terminal.setType(Material.LECTERN,false);mark(terminal,id);
        w.getBlockAt(x-7,y+1,z+1).setType(Material.CAMPFIRE,false);
        w.getBlockAt(x+7,y+1,z-1).setType(Material.CHAIN,false);
        w.getBlockAt(x+7,y+2,z-1).setType(Material.LANTERN,false);
        for(int[] c:new int[][]{{-7,1,0},{7,1,0},{0,1,4}})w.getBlockAt(x+c[0],y+c[1],z+c[2]).setType(Material.COMPOSTER,false);
    }
    private void mark(Block b,String id){org.bukkit.block.BlockState s=b.getState();if(s instanceof TileState tile){tile.getPersistentDataContainer().set(baseBlockKey,PersistentDataType.STRING,id);tile.update(true,false);}}
    private String baseOf(UUID player){
        ConfigurationSection all=data.getConfigurationSection("bases");if(all==null)return null;
        for(String id:all.getKeys(false))if(data.getString("bases."+id+".owner","").equals(player.toString()))return id;
        return null;
    }
    private String baseAt(Location l){
        ConfigurationSection all=data.getConfigurationSection("bases");if(all==null)return null;
        for(String id:all.getKeys(false)){String p="bases."+id;if(!data.getString(p+".world","").equals(l.getWorld().getName()))continue;
            int x=data.getInt(p+".x"),y=data.getInt(p+".y"),z=data.getInt(p+".z");
            if(Math.abs(l.getBlockX()-x)<=9&&Math.abs(l.getBlockY()-y)<=6&&Math.abs(l.getBlockZ()-z)<=6)return id;
        }return null;
    }
    private boolean allowed(Player p,String id,String permission){
        String path="bases."+id;UUID owner=UUID.fromString(data.getString(path+".owner"));
        if(owner.equals(p.getUniqueId()))return true;
        UUID team=teams.teamOf(p.getUniqueId());UUID ownerTeam=teams.teamOf(owner);
        if(team==null||ownerTeam==null||!team.equals(ownerTeam))return false;
        return data.getBoolean(path+".permissions."+p.getUniqueId()+"."+permission,permission.equals("enter"));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void movement(PlayerMoveEvent e){if(e.getTo()==null||e.getFrom().getBlockX()==e.getTo().getBlockX()&&e.getFrom().getBlockY()==e.getTo().getBlockY()&&e.getFrom().getBlockZ()==e.getTo().getBlockZ())return;String from=baseAt(e.getFrom()),to=baseAt(e.getTo());if(to!=null&&!to.equals(from)&&!allowed(e.getPlayer(),to,"enter")){e.setTo(e.getFrom());e.getPlayer().sendMessage(ChatColor.RED+"You are not permitted to enter this base.");}}
    private void securityTick(){
        if(!worlds.isActive()||worlds.getWorld()==null)return;
        ConfigurationSection all=data.getConfigurationSection("bases");if(all==null)return;
        long now=System.currentTimeMillis();
        for(String id:all.getKeys(false)){
            String path="bases."+id;World world=Bukkit.getWorld(data.getString(path+".world",""));
            if(world==null||!world.equals(worlds.getWorld()))continue;
            int x=data.getInt(path+".x"),y=data.getInt(path+".y"),z=data.getInt(path+".z");Kind k=kind(id);
            if(!data.getBoolean(path+".powered",false))continue;
            double radius=10+k.security*7.5;Location center=new Location(world,x,y+1,z);
            boolean threat=!world.getNearbyEntities(center,radius,8,radius,e->e instanceof Monster).isEmpty();
            if(!threat||now-lastSecurityAlert.getOrDefault(id,0L)<30_000L)continue;
            lastSecurityAlert.put(id,now);UUID owner=UUID.fromString(data.getString(path+".owner"));
            for(Player member:world.getPlayers())if(member.getUniqueId().equals(owner)||allowed(member,id,"security")){
                member.playSound(member.getLocation(),Sound.BLOCK_NOTE_BLOCK_BELL,0.8f,0.65f);
                member.sendMessage(ChatColor.RED+"[BASE SECURITY] Hostile movement detected near "+k.name+". Perimeter range: "+(int)radius+" blocks.");
            }
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void protect(BlockBreakEvent e){String id=baseAt(e.getBlock().getLocation());if(id==null)return;
        if(!allowed(e.getPlayer(),id,"enter")||!allowed(e.getPlayer(),id,"build")){e.setCancelled(true);e.getPlayer().sendMessage(ChatColor.RED+"You do not have base modification permission.");}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void place(BlockPlaceEvent e){String id=baseAt(e.getBlock().getLocation());if(id==null)return;
        if(!allowed(e.getPlayer(),id,"build")){e.setCancelled(true);e.getPlayer().sendMessage(ChatColor.RED+"You do not have base modification permission.");}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void useBlock(PlayerInteractEvent e){
        if(e.getHand()!=EquipmentSlot.HAND||e.getClickedBlock()==null)return;String id=baseAt(e.getClickedBlock().getLocation());if(id==null)return;
        if(!allowed(e.getPlayer(),id,"enter")){e.setCancelled(true);e.getPlayer().sendMessage(ChatColor.RED+"You are not permitted to enter this base.");return;}
        Material type=e.getClickedBlock().getType();
        if(type==Material.CHEST||type==Material.BARREL){if(!allowed(e.getPlayer(),id,"enter")||!allowed(e.getPlayer(),id,"storage")){e.setCancelled(true);e.getPlayer().sendMessage(ChatColor.RED+"Storage access is disabled for you.");}}
        if(type==Material.SMITHING_TABLE){e.setCancelled(true);if(allowed(e.getPlayer(),id,"forge"))openForge(e.getPlayer(),id);else e.getPlayer().sendMessage(ChatColor.RED+"Forge access is disabled for you.");}
        if(type==Material.LODESTONE){e.setCancelled(true);if(allowed(e.getPlayer(),id,"security"))e.getPlayer().sendMessage(ChatColor.AQUA+"Security tier "+kind(id).security+" / 4. Generator: "+(data.getBoolean("bases."+id+".powered",false)?"ONLINE":"OFFLINE")+".");else e.getPlayer().sendMessage(ChatColor.RED+"Security console access is disabled for you.");}
        if(type==Material.REDSTONE_LAMP){e.setCancelled(true);if(allowed(e.getPlayer(),id,"security"))e.getPlayer().sendMessage(ChatColor.YELLOW+"Base generator: "+(data.getBoolean("bases."+id+".powered",false)?"ONLINE":"OFFLINE")+". Use /base power to restore power.");}
        if(type==Material.LECTERN){e.setCancelled(true);if(allowed(e.getPlayer(),id,"research"))e.getPlayer().sendMessage(ChatColor.LIGHT_PURPLE+"Research terminal: artifact records and expedition logs are being indexed.");}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explode(EntityExplodeEvent e){e.blockList().removeIf(b->baseAt(b.getLocation())!=null);}
    private Kind kind(String id){String k=data.getString("bases."+id+".kind","");return Arrays.stream(Kind.values()).filter(v->v.id.equals(k)).findFirst().orElse(Kind.WORKSHOP);}
    private void openForge(Player p,String id){
        forgeBase.put(p.getUniqueId(),id);Inventory inv=Bukkit.createInventory(null,9,ChatColor.DARK_GRAY+"Artifact Forge");
        String[] names={"Null Fragment → Resonance Core","Resonance Core → Forgotten Charm","Forgotten Charm → Null Ward","Null Ward → Power Cell","Power Cell → Reconstructor"};
        for(int i=0;i<5;i++){ItemStack icon=new ItemStack(Material.AMETHYST_SHARD);ItemMeta m=icon.getItemMeta();m.setDisplayName(ChatColor.LIGHT_PURPLE+names[i]);m.setLore(List.of(ChatColor.GRAY+"Base time: "+HOURS[i]+" hour(s)",ChatColor.GRAY+"Forge-adjusted time: "+Math.max(1,Math.min(24,Math.round(HOURS[i]*kind(id).forge*100)/100.0))+" hour(s)",ChatColor.YELLOW+"Click to begin"));icon.setItemMeta(m);inv.setItem(i,icon);}
        ItemStack claim=new ItemStack(Material.CHEST);ItemMeta cm=claim.getItemMeta();cm.setDisplayName(ChatColor.GREEN+"Claim completed artifact");claim.setItemMeta(cm);inv.setItem(8,claim);
        p.openInventory(inv);
    }
    private void startRecipe(Player p,String id,int recipe){
        String input=RECIPES[recipe],output=OUTPUTS[recipe];
        int slot=-1;for(int i=0;i<p.getInventory().getSize();i++){ItemStack stack=p.getInventory().getItem(i);if(plugin.getCustomItems().isCustomItem(stack)&&plugin.getCustomItems().getItemId(stack).equals(input)){slot=i;break;}}
        if(slot<0){p.sendMessage(ChatColor.RED+"Required artifact: "+input.replace('_',' '));return;}
        String path="jobs."+p.getUniqueId();if(data.contains(path+".finish")){p.sendMessage(ChatColor.RED+"You already have an artifact in the forge queue.");return;}
        ItemStack stack=p.getInventory().getItem(slot);if(stack.getAmount()==1)p.getInventory().setItem(slot,null);else stack.setAmount(stack.getAmount()-1);
        long duration=Math.max(3_600_000L,Math.min(24L*3_600_000L,(long)(HOURS[recipe]*kind(id).forge*3_600_000L)));
        data.set(path+".finish",System.currentTimeMillis()+duration);data.set(path+".output",output);data.set(path+".base",id);data.set(path+".owner",p.getUniqueId().toString());save();
        p.sendMessage(ChatColor.LIGHT_PURPLE+"Forge started. It will finish in approximately "+Math.max(1,Math.round(duration/3600000.0*10)/10.0)+" hours. Offline time counts.");
    }
    private void claim(Player p,String id){
        String path="jobs."+p.getUniqueId();if(!data.contains(path+".finish")){p.sendMessage(ChatColor.GRAY+"No artifact is waiting.");return;}
        long remaining=data.getLong(path+".finish")-System.currentTimeMillis();if(remaining>0){p.sendMessage(ChatColor.YELLOW+"Forge remaining: "+Math.max(1,(remaining+59999)/60000)+" minute(s).");return;}
        String output=data.getString(path+".output");CustomItemManager.Type type=CustomItemManager.Type.byId(output).orElse(null);
        if(type==null){p.sendMessage(ChatColor.RED+"Forge output is invalid; the job has been preserved for an administrator.");return;}
        Map<Integer,ItemStack> leftovers=p.getInventory().addItem(plugin.getCustomItems().createItem(type));
        if(!leftovers.isEmpty()){p.sendMessage(ChatColor.RED+"Make room in your inventory before claiming.");return;}
        data.set(path,null);save();p.sendMessage(ChatColor.GREEN+"Artifact recovered: "+type.title);
    }
    private boolean hasCustom(Player p,String itemId,int amount){int count=0;for(ItemStack stack:p.getInventory().getContents())if(plugin.getCustomItems().isCustomItem(stack)&&plugin.getCustomItems().getItemId(stack).equals(itemId))count+=stack.getAmount();return count>=amount;}
    private boolean consumeCustom(Player p,String itemId,int amount){int count=0;for(ItemStack stack:p.getInventory().getContents())if(plugin.getCustomItems().isCustomItem(stack)&&plugin.getCustomItems().getItemId(stack).equals(itemId))count+=stack.getAmount();if(count<amount)return false;int left=amount;for(int i=0;i<p.getInventory().getSize()&&left>0;i++){ItemStack stack=p.getInventory().getItem(i);if(!plugin.getCustomItems().isCustomItem(stack)||!plugin.getCustomItems().getItemId(stack).equals(itemId))continue;int take=Math.min(left,stack.getAmount());if(take==stack.getAmount())p.getInventory().setItem(i,null);else stack.setAmount(stack.getAmount()-take);left-=take;}return true;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player p)){sender.sendMessage("Players only.");return true;}
        if(args.length==0||args[0].equalsIgnoreCase("info")){String id=baseOf(p.getUniqueId());if(id==null){p.sendMessage(ChatColor.GRAY+"You do not own a base.");return true;}Kind k=kind(id);p.sendMessage(ChatColor.GOLD+k.name+" §7| Storage: "+k.chests+" chests | Forge: "+k.forge+"x | Security: "+k.security+"/4");return true;}
        if(args[0].equalsIgnoreCase("power")){String id=baseOf(p.getUniqueId());if(id==null){p.sendMessage(ChatColor.RED+"You do not own a base.");return true;}if(!data.getBoolean("bases."+id+".powered",false)){if(!hasCustom(p,"insulated_wire",2)||!hasCustom(p,"fuse",2)||!hasCustom(p,"power_cell",1)){p.sendMessage(ChatColor.RED+"Power restoration requires 2 Insulated Wire, 2 Ceramic Fuses and 1 Resonance Power Cell. Nothing was consumed.");return true;}consumeCustom(p,"insulated_wire",2);consumeCustom(p,"fuse",2);consumeCustom(p,"power_cell",1);data.set("bases."+id+".powered",true);save();p.sendMessage(ChatColor.GREEN+"Generator restored. Security perimeter and base systems are now powered.");}else p.sendMessage(ChatColor.GREEN+"Generator is already online.");return true;}
        if(args[0].equalsIgnoreCase("permissions")&&args.length==4){
            String id=baseOf(p.getUniqueId());if(id==null){p.sendMessage(ChatColor.RED+"You do not own a base.");return true;}
            OfflinePlayer target=Bukkit.getOfflinePlayer(args[1]);UUID team=teams.teamOf(p.getUniqueId());
            if(target.getUniqueId().equals(p.getUniqueId())||team==null||!team.equals(teams.teamOf(target.getUniqueId()))){p.sendMessage(ChatColor.RED+"Permissions can only be assigned to a teammate.");return true;}
            String perm=args[2].toLowerCase(Locale.ROOT);if(!Arrays.asList(PERMS).contains(perm)){p.sendMessage(ChatColor.YELLOW+"Permissions: "+String.join(", ",PERMS));return true;}
            if(!args[3].equalsIgnoreCase("on")&&!args[3].equalsIgnoreCase("off")){p.sendMessage(ChatColor.YELLOW+"Use on or off.");return true;}
            data.set("bases."+id+".permissions."+target.getUniqueId()+"."+perm,args[3].equalsIgnoreCase("on"));save();
            p.sendMessage(ChatColor.GREEN+perm+" permission for "+target.getName()+" set to "+args[3]+".");return true;
        }
        p.sendMessage(ChatColor.YELLOW+"/base [info|power] | /base permissions <teammate> <enter|storage|forge|build|security|research> <on|off>");return true;
    }
    @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){
        if(args.length==1)return List.of("info","power","permissions").stream().filter(v->v.startsWith(args[0].toLowerCase())).toList();
        if(args.length==2&&args[0].equalsIgnoreCase("permissions"))return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if(args.length==3&&args[0].equalsIgnoreCase("permissions"))return List.of(PERMS);
        if(args.length==4&&args[0].equalsIgnoreCase("permissions"))return List.of("on","off");
        return List.of();
    }
}
