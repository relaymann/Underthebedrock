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
        if(!worlds.isActive())return;
        World world=worlds.getWorld(); if(world==null)return;
        int cell=Math.max(512,plugin.getConfig().getInt("horror.structures.site-cell-size-blocks",2048));
        for(Player player:world.getPlayers()){
            int cx=Math.floorDiv(player.getLocation().getBlockX()-world.getSpawnLocation().getBlockX(),cell);
            int cz=Math.floorDiv(player.getLocation().getBlockZ()-world.getSpawnLocation().getBlockZ(),cell);
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)
                ensureSite(world,cx+dx,cz+dz,cell,dx==0&&dz==0?player:null);
        }
    }
    private void ensureSite(World world,int cellX,int cellZ,int cell,Player explorer){
        long seed=world.getSeed()^((long)cellX*0x9E3779B97F4A7C15L)^((long)cellZ*0xC2B2AE3D27D4EB4FL);
        Random random=new Random(seed);
        String key=cellX+"_"+cellZ,path="sites."+key;
        StructureDefinition selected;
        int x,z;
        if(data.contains(path+".type")){
            selected=StructureDefinition.byId(data.getString(path+".type","")).orElse(null);
            if(selected==null)return;
            x=data.getInt(path+".x");z=data.getInt(path+".z");
            double relocationThreshold=Math.max(180,plugin.getConfig().getInt("horror.structures.maximum-site-offset-blocks",340)+32);
            if(!data.getBoolean(path+".built",false)&&explorer!=null
                    &&explorer.getLocation().distanceSquared(new Location(world,x,plugin.getConfig().getInt("world.floor-y",64)+1,z))>relocationThreshold*relocationThreshold){
                if(distanceFromSpawn(world,explorer.getLocation())<minimumSpawnDistance())return;
                int[] candidate=siteCandidate(world,explorer,random);x=candidate[0];z=candidate[1];
                if(!chunksLoaded(world,x,z,radius(selected))||!areaClear(world,x,z,radius(selected)))return;
                data.set(path+".x",x);data.set(path+".z",z);save();
            }
        }else{
            if(explorer==null||distanceFromSpawn(world,explorer.getLocation())<minimumSpawnDistance()
                    ||random.nextDouble()>plugin.getConfig().getDouble("horror.structures.cell-generation-chance",.24))return;
            int[] candidate=siteCandidate(world,explorer,random);x=candidate[0];z=candidate[1];
            double distance=Math.hypot(x-world.getSpawnLocation().getBlockX(),z-world.getSpawnLocation().getBlockZ());
            selected=StructureDefinition.select(distance,seed).orElse(null);
            if(selected==null||!chunksLoaded(world,x,z,radius(selected))||!areaClear(world,x,z,radius(selected)))return;
            data.set(path+".type",selected.id());data.set(path+".x",x);data.set(path+".z",z);
            data.set(path+".last-reset",System.currentTimeMillis());data.set(path+".built",false);save();
        }
        double spawnRadius=Math.max(96,plugin.getConfig().getInt("horror.structures.spawn-check-radius-blocks",160));
        Location siteCenter=new Location(world,x,plugin.getConfig().getInt("world.floor-y",64)+1,z);
        boolean playerNearby=false;
        for(Player player:world.getPlayers())if(player.getLocation().distanceSquared(siteCenter)<=spawnRadius*spawnRadius){playerNearby=true;break;}
        if(!playerNearby||!chunksLoaded(world,x,z,radius(selected)))return;
        long now=System.currentTimeMillis(),last=data.getLong(path+".last-reset",now);
        boolean first=!data.getBoolean(path+".built",false);
        long period=Math.max(1,plugin.getConfig().getLong("horror.structures.regeneration-hours",1))*HOUR;
        // One-time migration for previously generated labs: ensure both guaranteed
        // prototype rewards exist in the original cache without replacing player loot.
        if(selected==StructureDefinition.BASE_CONSTRUCTOR_LAB&&!first&&!data.getBoolean(path+".lab-rewards-issued",false)){
            int cacheY=plugin.getConfig().getInt("world.floor-y",64)+2;
            Block cache=world.getBlockAt(x-6,cacheY,z+5);
            if(cache.getState() instanceof Container container&&plugin.getBaseManager()!=null){
                ItemStack constructor=plugin.getBaseManager().constructorItem();
                ItemStack token=plugin.getBaseManager().upgradeTokenItem();
                if(!container.getInventory().containsAtLeast(constructor,1))container.getInventory().addItem(constructor);
                if(!container.getInventory().containsAtLeast(token,1))container.getInventory().addItem(token);
                container.update(true,false);data.set(path+".lab-rewards-issued",true);save();
            }
        }
        if(first||now-last>=period){
            if(selected==StructureDefinition.BASE_CONSTRUCTOR_LAB&&!first)return;
            if(!first){double safeDistance=Math.pow(radius(selected)+4,2);for(Player nearby:world.getPlayers())if(nearby.getLocation().distanceSquared(siteCenter)<=safeDistance)return;}
            int cycle=data.getInt(path+".cycle",0)+(first?0:1);
            build(world,x,z,plugin.getConfig().getInt("world.floor-y",64)+1,selected,seed,key,cycle);
            data.set(path+".built",true);data.set(path+".last-reset",now);data.set(path+".cycle",cycle);
            if(selected==StructureDefinition.BASE_CONSTRUCTOR_LAB)data.set(path+".lab-rewards-issued",true);
            save();
        }
    }
    private double minimumSpawnDistance(){
        return Math.max(0,plugin.getConfig().getDouble("horror.structures.minimum-distance-from-spawn-blocks",600));
    }
    private double distanceFromSpawn(World world,Location location){
        Location spawn=world.getSpawnLocation();
        return Math.hypot(location.getX()-spawn.getX(),location.getZ()-spawn.getZ());
    }
    private int[] siteCandidate(World world,Player explorer,Random random){
        Location player=explorer.getLocation(),spawn=world.getSpawnLocation();
        int minimum=Math.max(64,plugin.getConfig().getInt("horror.structures.minimum-site-offset-blocks",200));
        int maximum=Math.max(minimum,plugin.getConfig().getInt("horror.structures.maximum-site-offset-blocks",340));
        int offset=minimum+random.nextInt(maximum-minimum+1);
        double angle=random.nextDouble()*Math.PI*2;
        int x=player.getBlockX()+(int)Math.round(Math.cos(angle)*offset);
        int z=player.getBlockZ()+(int)Math.round(Math.sin(angle)*offset);
        if(Math.hypot(x-spawn.getX(),z-spawn.getZ())<minimumSpawnDistance()){
            angle=Math.atan2(player.getZ()-spawn.getZ(),player.getX()-spawn.getX());
            if(Math.abs(player.getX()-spawn.getX())+Math.abs(player.getZ()-spawn.getZ())<1)angle=random.nextDouble()*Math.PI*2;
            x=player.getBlockX()+(int)Math.round(Math.cos(angle)*offset);
            z=player.getBlockZ()+(int)Math.round(Math.sin(angle)*offset);
        }
        return new int[]{x,z};
    }
    private int radius(StructureDefinition type){
        return switch(type){
            case EMPTY_SETTLEMENT,RESEARCH_FACILITY,BASE_CONSTRUCTOR_LAB,CONTAINMENT_FACILITY,ARCHIVE_HALL->17;
            case MONOLITH,SEALED_GATEWAY,RAILWAY,IMPOSSIBLE_CORRIDOR,ERASURE_VAULT->15;
            case UNDERGROUND_BUNKER,FORGOTTEN_CHAPEL,KEEPER_SANCTUARY,WITNESS_REFUGE->13;
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
        Location l=event.getBlock().getLocation();int cell=Math.max(256,plugin.getConfig().getInt("horror.structures.site-cell-size-blocks",512));
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
            case RESEARCH_FACILITY->{b.room(-7,7,-5,5,"POLISHED_ANDESITE","IRON_BLOCK");b.open(0,1,-5);b.open(0,2,-5);b.put(-4,1,-2,"IRON_BARS");b.put(-4,2,-2,"IRON_BARS");b.put(0,1,0,"BREWING_STAND");b.put(2,1,0,"LECTERN");b.put(4,1,2,"CAULDRON");b.chest(-5,1,3,loot,5);b.chest(5,1,-3,loot,5);if(loot.nextDouble()<0.12)b.baseConstructor(-5,1,3);if(loot.nextDouble()<plugin.getConfig().getDouble("horror.structures.upgrade-token-chance",0.005))b.upgradeToken(-5,1,3);b.sign(0,1,-4,"OBSERVATION 04","SUBJECT RESPONDS","TO CHAT");b.loreSign(-6,1,4,"protection_system","BOUNDARY SAFEGUARD","TEST: INCOMPLETE");b.memoryShard(-5,1,3,loot,"protection_loop");}
            case BASE_CONSTRUCTOR_LAB->{b.room(-8,8,-8,8,"POLISHED_DEEPSLATE","IRON_BLOCK");b.open(0,1,-8);b.open(0,2,-8);for(int i=-5;i<=5;i+=5){b.put(i,1,-4,"IRON_BARS");b.put(i,2,-4,"IRON_BARS");b.put(i,1,4,"IRON_BARS");b.put(i,2,4,"IRON_BARS");}b.put(-5,1,0,"BREWING_STAND");b.put(-5,1,1,"BLAST_FURNACE");b.put(5,1,0,"SMITHING_TABLE");b.put(5,1,1,"ANVIL");b.put(0,1,0,"RESPAWN_ANCHOR");b.put(0,2,0,"AMETHYST_BLOCK");b.put(-2,1,-2,"OBSERVER");b.put(2,1,-2,"OBSERVER");b.put(-2,1,2,"OBSERVER");b.put(2,1,2,"OBSERVER");b.chest(-6,1,5,loot,5);b.baseConstructor(-6,1,5);b.upgradeToken(-6,1,5);b.loreSign(-7,1,-7,"constructor_project","PROJECT: CONSTRUCTOR","PROTOTYPE DEPLOYMENT");b.loreSign(7,1,-7,"constructor_failures","ITERATION 09","THE SITE MUST BE ABANDONED");b.sign(0,1,7,"DO NOT ACTIVATE","INSIDE THE LAB","TAKE IT TO A SAFE SITE");}
            case EMPTY_SETTLEMENT->{b.room(-10,-4,-4,4,"OAK_PLANKS","OAK_LOG");b.room(4,10,-4,4,"SPRUCE_PLANKS","SPRUCE_LOG");b.room(-3,3,5,10,"BIRCH_PLANKS","BIRCH_LOG");b.put(-7,1,0,"RED_BED");b.put(7,1,0,"WHITE_BED");b.put(0,1,7,"FARMLAND");b.chest(-8,1,3,loot,4);b.sign(0,2,11,"POPULATION: 0","LAST SEEN:","YOU");b.loreSign(-9,2,-3,"population_ledger","FINAL CENSUS","NO MATCHING NAMES");b.loreSign(8,2,-3,"resident_file","RESIDENT FILE","PERSONAL COPY");}
            case UNDERGROUND_BUNKER->{b.room(-6,6,-5,5,"DEEPSLATE_BRICKS","IRON_BLOCK");b.put(0,1,0,"IRON_DOOR");b.put(-3,1,-2,"LANTERN");b.put(3,1,-2,"LANTERN");b.chest(0,1,3,loot,5);b.sign(0,2,-5,"SHELTER SEALED","FROM THE INSIDE");}
            case RAILWAY->{for(int i=-7;i<=7;i++){b.put(0,0,i,"RAIL");b.put(1,0,i,"DEEPSLATE_BRICKS");}b.put(2,0,6,"POWERED_RAIL");b.put(-3,1,0,"SOUL_LANTERN");b.chest(-3,1,5,loot,3);b.sign(3,1,0,"NEXT TRAIN:","NEVER");b.loreSign(3,2,5,"last_day","FINAL SERVICE LOG","NO DEPARTURES");b.memoryShard(-3,1,5,loot,"final_hour");}
            case FORGOTTEN_CHAPEL->{b.room(-6,6,-7,7,"STONE_BRICKS","MOSSY_STONE_BRICKS");b.put(0,1,-2,"LECTERN");b.put(0,2,-2,"BOOKSHELF");b.put(-4,1,0,"CANDLE");b.put(4,1,0,"CANDLE");b.put(0,1,5,"CRYING_OBSIDIAN");b.chest(-4,1,5,loot,3);b.sign(0,2,7,"NO ONE PRAYS","HERE ANYMORE");}
            case SEALED_GATEWAY->{for(int h=0;h<=7;h++){b.put(-5,h,0,"DEEPSLATE_BRICKS");b.put(5,h,0,"DEEPSLATE_BRICKS");}for(int i=-5;i<=5;i++)b.put(i,7,0,"DEEPSLATE_BRICKS");b.put(0,1,0,"CRYING_OBSIDIAN");b.put(0,2,0,"CRYING_OBSIDIAN");b.sign(0,2,2,"NOT AN EXIT");}
            case CONTAINMENT_FACILITY->{b.room(-7,7,-7,7,"POLISHED_BLACKSTONE","IRON_BLOCK");for(int i=-3;i<=3;i++){b.put(i,1,0,"IRON_BARS");b.put(i,2,0,"IRON_BARS");}b.put(0,1,0,"SPAWNER");b.put(-5,1,-5,"REDSTONE_LAMP");b.put(5,1,5,"REDSTONE_LAMP");b.chest(-5,1,5,loot,5);b.sign(0,3,7,"CONTAINMENT","FAILED","DO NOT OPEN");}
            case IMPOSSIBLE_CORRIDOR->{for(int i=-7;i<=7;i++){b.put(-2,0,i,"DEEPSLATE_TILES");b.put(2,0,i,"DEEPSLATE_TILES");for(int h=1;h<=3;h++){b.put(-2,h,i,"POLISHED_BLACKSTONE");b.put(2,h,i,"POLISHED_BLACKSTONE");}}b.put(0,1,-6,"IRON_DOOR");b.put(0,1,6,"IRON_DOOR");b.sign(0,2,0,"DISTANCE TRAVELED:","0 BLOCKS");}
            case MONOLITH->{for(int h=0;h<13;h++){int w=Math.max(1,5-h/3);for(int dx=-w;dx<=w;dx++)for(int dz=-w;dz<=w;dz++)b.put(dx,h,dz,"POLISHED_BLACKSTONE");}b.put(0,13,0,"CRYING_OBSIDIAN");b.put(0,14,0,"AMETHYST_BLOCK");b.sign(0,2,4,"YOU CAME BACK");}
            case ARCHIVE_HALL->{b.room(-7,7,-6,6,"STONE_BRICKS","CHISELED_BOOKSHELF");for(int bx=-5;bx<=5;bx+=2){b.put(bx,1,-4,"BOOKSHELF");b.put(bx,2,-4,"BOOKSHELF");}b.put(-3,1,0,"LECTERN");b.put(3,1,0,"LECTERN");b.chest(-5,1,4,loot,4);b.chest(5,1,4,loot,4);b.loreSign(-3,2,-5,"architects","THE ARCHITECTS","WE DREW THE LINE");b.loreSign(3,2,-5,"researchers","THE RESEARCHERS","IT ANSWERED FIRST");b.memoryShard(-5,1,4,loot,"first_blueprint");b.memoryShard(5,1,4,loot,"first_contact");}
            case KEEPER_SANCTUARY->{b.room(-6,6,-6,6,"MOSSY_STONE_BRICKS","POLISHED_BLACKSTONE");b.put(0,1,0,"CRYING_OBSIDIAN");b.put(0,2,0,"AMETHYST_BLOCK");b.put(-4,1,-3,"SOUL_LANTERN");b.put(4,1,-3,"SOUL_LANTERN");b.chest(-4,1,4,loot,3);b.loreSign(0,2,-6,"keepers","THE KEEPERS","WE KEPT HIS NAME");b.memoryShard(-4,1,4,loot,"removal_attempt");b.rareArtifact(-4,1,4,loot);}
            case WITNESS_REFUGE->{b.room(-6,6,-6,6,"SPRUCE_PLANKS","DARK_OAK_LOG");b.put(-3,1,-2,"RED_BED");b.put(3,1,-2,"RED_BED");b.put(0,1,2,"CAMPFIRE");b.chest(4,1,4,loot,3);b.loreSign(-3,2,-6,"witnesses","THE WITNESSES","IT WORE HIS VOICE");b.loreSign(3,2,-6,"erased","THE ERASED","WE CANNOT REMEMBER");b.loreSign(-5,2,5,"decades_silence","MAINTENANCE LOG","LAST AUTOMATION: YEARS LATER");b.memoryShard(4,1,4,loot,"identity_fragment");}
            case ERASURE_VAULT->{for(int h=0;h<=8;h++){for(int dx=-6;dx<=6;dx++){b.put(dx,h,-6,"DEEPSLATE_BRICKS");b.put(dx,h,6,"DEEPSLATE_BRICKS");}for(int dz=-5;dz<=5;dz++){b.put(-6,h,dz,"DEEPSLATE_BRICKS");b.put(6,h,dz,"DEEPSLATE_BRICKS");}}b.put(0,1,0,"CRYING_OBSIDIAN");b.put(0,2,0,"RESPAWN_ANCHOR");b.put(0,3,0,"AMETHYST_BLOCK");b.loreSign(0,2,-6,"identity_case","ORIGINAL IDENTITY","CASE FILE 0");b.loreSign(0,2,6,"orphaned_identity","ORPHANED IDENTITY","SUBJECT: UNRESOLVED");b.loreSign(4,2,6,"recognition_event","RETURN SIGNAL","SUBJECT PRESENT");b.chest(3,1,3,loot,4);b.memoryShard(3,1,3,loot,"recognition");}
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
                if(container.getInventory().isEmpty()){Material[] loot={Material.BREAD,Material.TORCH,Material.IRON_INGOT,Material.STRING,Material.PAPER,Material.COMPASS,Material.CLOCK,Material.GOLD_NUGGET,Material.ARROW};
                for(int i=0;i<rolls;i++)container.getInventory().addItem(new ItemStack(loot[random.nextInt(loot.length)],1+random.nextInt(4)));
                if(plugin.getCustomItems()!=null){for(int n=0;n<1+random.nextInt(2);n++)container.getInventory().addItem(plugin.getCustomItems().randomLootItem(random));}}
                container.getPersistentDataContainer().set(marker,PersistentDataType.STRING,site);container.update(true,false);
            }
        }
        void baseConstructor(int x,int y,int z){Location location=new Location(world,ox+x,oy+y,oz+z);Block block=location.getBlock();if(plugin.getBaseManager()!=null&&block.getState() instanceof Container container){ItemStack item=plugin.getBaseManager().constructorItem();if(!container.getInventory().containsAtLeast(item,1)){container.getInventory().addItem(item);container.update(true,false);}}}
         void upgradeToken(int x,int y,int z){
            Location location=new Location(world,ox+x,oy+y,oz+z);
            if(location.getBlock().getState() instanceof Container container&&plugin.getBaseManager()!=null){
                ItemStack token=plugin.getBaseManager().upgradeTokenItem();
                if(!container.getInventory().containsAtLeast(token,1)){
                    container.getInventory().addItem(token);container.update(true,false);
                }
            }
        }
        void loreSign(int x,int y,int z,String id,String... lines){sign(x,y,z,lines);Block block=new Location(world,ox+x,oy+y,oz+z).getBlock();if(block.getState() instanceof Sign sign){plugin.getLoreManager().tagDocument(sign,id);sign.update(true,false);}}
        void rareArtifact(int x,int y,int z,Random random){if(random.nextDouble()>plugin.getConfig().getDouble("horror.structures.rare-artifact-chance",.04))return;Location l=new Location(world,ox+x,oy+y,oz+z);if(l.getBlock().getState() instanceof Container c){ItemStack seal=plugin.getLoreManager().createArchitectSeal();if(!c.getInventory().containsAtLeast(seal,1)){c.getInventory().addItem(seal);c.update(true,false);}}}
        void memoryShard(int x,int y,int z,Random random,String recording){if(!plugin.getConfig().getBoolean("horror.structures.ancient-factions-enabled",true))return;if(random.nextDouble()>plugin.getConfig().getDouble("horror.structures.memory-shard-chance",.7))return;Location l=new Location(world,ox+x,oy+y,oz+z);if(l.getBlock().getState() instanceof Container c){ItemStack shard=plugin.getLoreManager().createMemoryShard(recording);if(!c.getInventory().containsAtLeast(shard,1)){c.getInventory().addItem(shard);c.update(true,false);}}}
        void sign(int x,int y,int z,String... lines){
            Location location=new Location(world,ox+x,oy+y,oz+z);
            if(protectedBlocks.contains(key(location)))return;
            put(x,y,z,"OAK_SIGN");Block block=location.getBlock();
            if(block.getState() instanceof Sign sign){for(int i=0;i<4;i++)sign.setLine(i,i<lines.length?ChatColor.DARK_GRAY+lines[i]:"");sign.getPersistentDataContainer().set(marker,PersistentDataType.STRING,site);sign.update(true,false);}
        }
    }
}
