package dev.underthebedrock.world;

import dev.underthebedrock.UnderTheBedrockPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.scheduler.BukkitTask;
import java.io.File;
import java.io.IOException;
import java.util.*;

/** Deterministic structures, chunk-safe placement, protected player edits, and hourly restoration. */
public final class StructureManager implements Listener {
    private static final long HOUR=3_600_000L;
    private final UnderTheBedrockPlugin plugin;
    private final VoidWorldManager worlds;
    private final File file;
    private final YamlConfiguration data;
    private final Set<String> protectedBlocks=new HashSet<>();
    private final NamespacedKey marker;
    private BukkitTask task;

    public StructureManager(UnderTheBedrockPlugin plugin,VoidWorldManager worlds){
        this.plugin=plugin;this.worlds=worlds;file=new File(plugin.getDataFolder(),"structures.yml");
        data=YamlConfiguration.loadConfiguration(file);protectedBlocks.addAll(data.getStringList("protected-blocks"));
        marker=new NamespacedKey(plugin,"generated-structure");
    }
    public void start(){
        if(task!=null)return;
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        long interval=Math.max(40,plugin.getConfig().getLong("horror.structures.scan-interval-ticks",100));
        task=plugin.getServer().getScheduler().runTaskTimer(plugin,this::tick,60,interval);
    }
    public void stop(){if(task!=null)task.cancel();task=null;save();}

    private void tick(){
        if(!worlds.isActive())return; World world=worlds.getWorld(); if(world==null)return;
        int cell=Math.max(256,plugin.getConfig().getInt("horror.structures.site-cell-size-blocks",1024));
        for(Player player:world.getPlayers()){
            int cx=Math.floorDiv(player.getLocation().getBlockX()-world.getSpawnLocation().getBlockX(),cell);
            int cz=Math.floorDiv(player.getLocation().getBlockZ()-world.getSpawnLocation().getBlockZ(),cell);
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)ensureSite(world,cx+dx,cz+dz,cell);
        }
    }
    private void ensureSite(World world,int cellX,int cellZ,int cell){
        long seed=world.getSeed()^((long)cellX*0x9E3779B97F4A7C15L)^((long)cellZ*0xC2B2AE3D27D4EB4FL);
        Random random=new Random(seed);
        if(random.nextDouble()>plugin.getConfig().getDouble("horror.structures.cell-generation-chance",.32))return;
        int ox=cellX*cell+cell/2+random.nextInt(Math.max(1,cell/2))-cell/4;
        int oz=cellZ*cell+cell/2+random.nextInt(Math.max(1,cell/2))-cell/4;
        StructureDefinition selected=StructureDefinition.select(Math.hypot(ox,oz),seed).orElse(null);
        if(selected==null)return;
        String key=cellX+"_"+cellZ,path="sites."+key;
        int x=world.getSpawnLocation().getBlockX()+ox,z=world.getSpawnLocation().getBlockZ()+oz;
        StructureDefinition type=selected;
        if(data.contains(path+".type")){
            type=StructureDefinition.byId(data.getString(path+".type","")).orElse(selected);
            x=data.getInt(path+".x",x);z=data.getInt(path+".z",z);
        }else{
            if(!chunksLoaded(world,x,z,radius(type))||!areaClear(world,x,z,radius(type)))return;
            data.set(path+".type",type.id());data.set(path+".x",x);data.set(path+".z",z);
            data.set(path+".last-reset",System.currentTimeMillis());data.set(path+".built",false);save();
        }
        // Wait until a player is close enough that the site's footprint should already be naturally loaded.
        // This prevents distant cell scans from silently skipping structures before exploration reaches them.
        double spawnRadius=Math.max(32,plugin.getConfig().getInt("horror.structures.spawn-check-radius-blocks",96));
        Location siteCenter=new Location(world,x,plugin.getConfig().getInt("world.floor-y",64)+1,z);
        boolean playerNearby=false;
        for(Player player:world.getPlayers()){
            if(player.getLocation().distanceSquared(siteCenter)<=spawnRadius*spawnRadius){playerNearby=true;break;}
        }
        if(!playerNearby||!chunksLoaded(world,x,z,radius(type)))return;
        long now=System.currentTimeMillis(),last=data.getLong(path+".last-reset",now);
        boolean first=!data.getBoolean(path+".built",false);
        long period=Math.max(1,plugin.getConfig().getLong("horror.structures.regeneration-hours",1))*HOUR;
        if(first||now-last>=period){
            if(!first){
                Location center=new Location(world,x,plugin.getConfig().getInt("world.floor-y",64)+1,z);
                double safeDistance=Math.pow(radius(type)+4,2);
                for(Player nearby:world.getPlayers())if(nearby.getLocation().distanceSquared(center)<=safeDistance)return;
            }
            int cycle=data.getInt(path+".cycle",0)+(first?0:1);
            build(world,x,z,plugin.getConfig().getInt("world.floor-y",64)+1,type,seed,key,cycle);
            data.set(path+".built",true);data.set(path+".last-reset",now);
            data.set(path+".cycle",cycle);save();
        }
    }
    private int radius(StructureDefinition type){
        return switch(type){
            case EMPTY_SETTLEMENT,RESEARCH_FACILITY,CONTAINMENT_FACILITY->17;
            case MONOLITH,SEALED_GATEWAY,RAILWAY,IMPOSSIBLE_CORRIDOR->15;
            case UNDERGROUND_BUNKER,FORGOTTEN_CHAPEL->13;
            default->10;
        };
    }
    private boolean chunksLoaded(World world,int x,int z,int r){
        for(int cx=Math.floorDiv(x-r,16);cx<=Math.floorDiv(x+r,16);cx++)
            for(int cz=Math.floorDiv(z-r,16);cz<=Math.floorDiv(z+r,16);cz++)
                if(!world.isChunkLoaded(cx,cz))return false;
        return true;
    }
    private boolean areaClear(World world,int x,int z,int r){
        int y=plugin.getConfig().getInt("world.floor-y",64)+1;
        for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++)
            for(int dy=0;dy<=8;dy++)if(!world.getBlockAt(x+dx,y+dy,z+dz).getType().isAir())return false;
        return true;
    }
    private String key(Location l){return l.getWorld().getName()+":"+l.getBlockX()+":"+l.getBlockY()+":"+l.getBlockZ();}
    @EventHandler(ignoreCancelled=true) public void onPlace(BlockPlaceEvent event){
        if(!worlds.isActive()||!event.getBlock().getWorld().equals(worlds.getWorld()))return;
        Location l=event.getBlock().getLocation();int cell=Math.max(256,plugin.getConfig().getInt("horror.structures.site-cell-size-blocks",1024));
        int cx=Math.floorDiv(l.getBlockX()-worlds.getWorld().getSpawnLocation().getBlockX(),cell);
        int cz=Math.floorDiv(l.getBlockZ()-worlds.getWorld().getSpawnLocation().getBlockZ(),cell);
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            String p="sites."+(cx+dx)+"_"+(cz+dz);if(!data.contains(p+".type"))continue;
            StructureDefinition t=StructureDefinition.byId(data.getString(p+".type","")).orElse(null);if(t==null)continue;
            int sx=data.getInt(p+".x"),sz=data.getInt(p+".z"),floor=plugin.getConfig().getInt("world.floor-y",64)+1;
            if(Math.abs(l.getBlockX()-sx)<=radius(t)&&Math.abs(l.getBlockZ()-sz)<=radius(t)
                    &&l.getBlockY()>=floor&&l.getBlockY()<=floor+18){
                protectedBlocks.add(key(l));save();return;
            }
        }
    }
    @EventHandler(ignoreCancelled=true) public void onBreak(BlockBreakEvent event){
        String k=key(event.getBlock().getLocation());
        if(protectedBlocks.remove(k))save();
    }
    private void build(World world,int x,int z,int y,StructureDefinition type,long seed,String site,int cycle){
        Builder b=new Builder(world,x,y,z,site);
        Random loot=new Random(seed^(cycle*0x9E3779B97F4A7C15L));
        switch(type){
            case CAMPSITE->{b.put(-3,0,-2,"CAMPFIRE");b.put(3,0,-2,"SPRUCE_FENCE");b.put(3,1,-2,"WHITE_WOOL");b.put(3,1,-1,"WHITE_WOOL");b.put(3,1,0,"WHITE_WOOL");b.put(3,1,1,"WHITE_WOOL");b.put(2,1,1,"WHITE_WOOL");b.put(4,1,1,"WHITE_WOOL");b.chest(-2,0,2,loot,2);b.sign(-1,1,3,"WE LEFT AT","THE SAME TIME","DO NOT FOLLOW","THE LIGHT");}
            case CABIN->{b.room(-4,4,-3,3,"SPRUCE_PLANKS","SPRUCE_LOG");b.open(0,1,-3);b.open(0,2,-3);b.put(0,1,0,"RED_BED");b.put(2,1,1,"FURNACE");b.chest(-2,1,1,loot,3);b.sign(0,2,-3,"IF THE DOOR","OPENS, STAY","IN BED");}
            case SIGNAL_STATION->{b.platform(-5,5,-4,4,"DEEPSLATE_TILES");b.put(0,1,0,"LIGHTNING_ROD");b.put(0,2,0,"LIGHTNING_ROD");b.put(-2,1,1,"NOTE_BLOCK");b.put(2,1,1,"JUKEBOX");b.put(0,1,3,"REDSTONE_LAMP");b.chest(-3,1,-2,loot,4);b.sign(0,2,-4,"SIGNAL LOG 07","NO TRANSMITTER","WAS INSTALLED");}
            case OBSERVATION_TOWER->{b.tower(4);b.put(0,6,0,"SOUL_LANTERN");b.chest(1,4,1,loot,3);b.sign(0,5,2,"YOU ARE NOT","THE FIRST ONE","UP HERE");}
            case NULL_SHRINE->{b.platform(-4,4,-4,4,"POLISHED_BLACKSTONE_BRICKS");b.put(0,1,0,"CRYING_OBSIDIAN");b.put(0,2,0,"RESPAWN_ANCHOR");for(int i=-3;i<=3;i+=2){b.put(i,1,-3,"BLACKSTONE_WALL");b.put(i,1,3,"BLACKSTONE_WALL");}b.put(-3,1,0,"SOUL_LANTERN");b.put(3,1,0,"SOUL_LANTERN");b.sign(0,2,4,"IT KNEW","YOU WOULD","FIND THIS");}
            case RESEARCH_FACILITY->{b.room(-7,7,-5,5,"POLISHED_ANDESITE","IRON_BLOCK");b.put(-4,1,-2,"IRON_BARS");b.put(-4,2,-2,"IRON_BARS");b.put(0,1,0,"BREWING_STAND");b.put(2,1,0,"LECTERN");b.put(4,1,2,"CAULDRON");b.chest(-5,1,3,loot,5);b.chest(5,1,-3,loot,5);b.sign(0,2,-5,"OBSERVATION 04","SUBJECT RESPONDS","TO CHAT");}
            case EMPTY_SETTLEMENT->{b.room(-10,-4,-4,4,"OAK_PLANKS","OAK_LOG");b.room(4,10,-4,4,"SPRUCE_PLANKS","SPRUCE_LOG");b.room(-3,3,5,10,"BIRCH_PLANKS","BIRCH_LOG");b.put(-7,1,0,"RED_BED");b.put(7,1,0,"WHITE_BED");b.put(0,1,7,"FARMLAND");b.chest(-8,1,3,loot,4);b.sign(0,2,11,"POPULATION: 0","LAST SEEN:","YOU");}
            case UNDERGROUND_BUNKER->{b.room(-6,6,-5,5,"DEEPSLATE_BRICKS","IRON_BLOCK");b.put(0,1,0,"IRON_DOOR");b.put(-3,1,-2,"LANTERN");b.put(3,1,-2,"LANTERN");b.chest(0,1,3,loot,5);b.sign(0,2,-5,"SHELTER SEALED","FROM THE INSIDE");}
            case RAILWAY->{for(int i=-7;i<=7;i++){b.put(0,0,i,"RAIL");b.put(1,0,i,"DEEPSLATE_BRICKS");}b.put(2,0,6,"POWERED_RAIL");b.put(-3,1,0,"SOUL_LANTERN");b.chest(-3,1,5,loot,3);b.sign(3,1,0,"NEXT TRAIN:","NEVER");}
            case FORGOTTEN_CHAPEL->{b.room(-6,6,-7,7,"STONE_BRICKS","MOSSY_STONE_BRICKS");b.put(0,1,-2,"LECTERN");b.put(0,2,-2,"BOOKSHELF");b.put(-4,1,0,"CANDLE");b.put(4,1,0,"CANDLE");b.put(0,1,5,"CRYING_OBSIDIAN");b.chest(-4,1,5,loot,3);b.sign(0,2,7,"NO ONE PRAYS","HERE ANYMORE");}
            case SEALED_GATEWAY->{for(int h=0;h<=7;h++){b.put(-5,h,0,"DEEPSLATE_BRICKS");b.put(5,h,0,"DEEPSLATE_BRICKS");}for(int i=-5;i<=5;i++)b.put(i,7,0,"DEEPSLATE_BRICKS");b.put(0,1,0,"CRYING_OBSIDIAN");b.put(0,2,0,"CRYING_OBSIDIAN");b.sign(0,2,2,"NOT AN EXIT");}
            case CONTAINMENT_FACILITY->{b.room(-7,7,-7,7,"POLISHED_BLACKSTONE","IRON_BLOCK");for(int i=-3;i<=3;i++){b.put(i,1,0,"IRON_BARS");b.put(i,2,0,"IRON_BARS");}b.put(0,1,0,"SPAWNER");b.put(-5,1,-5,"REDSTONE_LAMP");b.put(5,1,5,"REDSTONE_LAMP");b.chest(-5,1,5,loot,5);b.sign(0,3,7,"CONTAINMENT","FAILED","DO NOT OPEN");}
            case IMPOSSIBLE_CORRIDOR->{for(int i=-7;i<=7;i++){b.put(-2,0,i,"DEEPSLATE_TILES");b.put(2,0,i,"DEEPSLATE_TILES");for(int h=1;h<=3;h++){b.put(-2,h,i,"POLISHED_BLACKSTONE");b.put(2,h,i,"POLISHED_BLACKSTONE");}}b.put(0,1,-6,"IRON_DOOR");b.put(0,1,6,"IRON_DOOR");b.sign(0,2,0,"DISTANCE TRAVELED:","0 BLOCKS");}
            case MONOLITH->{for(int h=0;h<13;h++){int w=Math.max(1,5-h/3);for(int dx=-w;dx<=w;dx++)for(int dz=-w;dz<=w;dz++)b.put(dx,h,dz,"POLISHED_BLACKSTONE");}b.put(0,13,0,"CRYING_OBSIDIAN");b.put(0,14,0,"AMETHYST_BLOCK");b.sign(0,2,4,"YOU CAME BACK");}
        }
    }
    private void save(){try{data.set("protected-blocks",new ArrayList<>(protectedBlocks));data.save(file);}catch(IOException e){plugin.getLogger().severe("Could not save structure data: "+e.getMessage());}}

    private final class Builder{
        private final World world;private final int ox,oy,oz;private final String site;
        Builder(World world,int x,int y,int z,String site){this.world=world;ox=x;oy=y;oz=z;this.site=site;}
        void put(int dx,int dy,int dz,String name){
            Location l=new Location(world,ox+dx,oy+dy,oz+dz);if(protectedBlocks.contains(key(l)))return;
            Material material=Material.matchMaterial(name);if(material==null||!material.isBlock())return;
            Block block=l.getBlock();if(!block.getType().isAir()&&!block.getType().equals(material))return;
            if(block.getType().isAir())block.setType(material,false);
            if(block.getState() instanceof org.bukkit.block.TileState tile){tile.getPersistentDataContainer().set(marker,PersistentDataType.STRING,site);tile.update(true,false);}
        }
        void open(int dx,int dy,int dz){Location l=new Location(world,ox+dx,oy+dy,oz+dz);if(!protectedBlocks.contains(key(l)))l.getBlock().setType(Material.AIR,false);}
        void platform(int minX,int maxX,int minZ,int maxZ,String floor){for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)put(x,0,z,floor);}
        void room(int minX,int maxX,int minZ,int maxZ,String wall,String trim){
            for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){
                put(x,0,z,"POLISHED_ANDESITE");
                if(x==minX||x==maxX||z==minZ||z==maxZ){put(x,1,z,wall);put(x,2,z,wall);put(x,3,z,trim);}
            }
        }
        void tower(int height){for(int h=0;h<=height;h++){put(-1,h,-1,"SPRUCE_LOG");put(1,h,-1,"SPRUCE_LOG");put(-1,h,1,"SPRUCE_LOG");put(1,h,1,"SPRUCE_LOG");}platform(-2,2,-2,2,"SPRUCE_PLANKS");for(int h=height+1;h<=height+2;h++)for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)if(Math.abs(dx)==2||Math.abs(dz)==2)put(dx,h,dz,"OAK_FENCE");}
        void chest(int x,int y,int z,Random random,int rolls){
            Location location=new Location(world,ox+x,oy+y,oz+z);
            if(protectedBlocks.contains(key(location)))return;
            put(x,y,z,"CHEST");Block block=location.getBlock();
            if(block.getState() instanceof Container container){
                container.getInventory().clear();Material[] loot={Material.BREAD,Material.TORCH,Material.IRON_INGOT,Material.STRING,Material.PAPER,Material.COMPASS,Material.CLOCK,Material.GOLD_NUGGET,Material.ARROW};
                for(int i=0;i<rolls;i++)container.getInventory().addItem(new ItemStack(loot[random.nextInt(loot.length)],1+random.nextInt(4)));
                if(plugin.getCustomItems()!=null){var choices=plugin.getCustomItems().allItems();for(int n=0;n<1+random.nextInt(2);n++)container.getInventory().addItem(plugin.getCustomItems().createItem(choices.get(random.nextInt(choices.size()))));}
                container.getPersistentDataContainer().set(marker,PersistentDataType.STRING,site);container.update(true,false);
            }
        }
        void sign(int x,int y,int z,String... lines){
            Location location=new Location(world,ox+x,oy+y,oz+z);
            if(protectedBlocks.contains(key(location)))return;
            put(x,y,z,"OAK_SIGN");Block block=location.getBlock();
            if(block.getState() instanceof Sign sign){for(int i=0;i<4;i++)sign.setLine(i,i<lines.length?ChatColor.DARK_GRAY+lines[i]:"");sign.getPersistentDataContainer().set(marker,PersistentDataType.STRING,site);sign.update(true,false);}
        }
    }
}
