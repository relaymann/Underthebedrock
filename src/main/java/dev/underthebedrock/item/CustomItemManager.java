package dev.underthebedrock.item;

import dev.underthebedrock.UnderTheBedrockPlugin;
import dev.underthebedrock.horror.*;
import dev.underthebedrock.world.VoidWorldManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitTask;
import java.io.File;
import java.io.IOException;
import java.util.*;

public final class CustomItemManager implements Listener {
 public enum Type {
  DISTORTED_COMPASS("distorted_compass","Distorted Compass",Material.COMPASS,"Points to a nearby registered site, but may lie."),
  SIGNAL_SPYGLASS("signal_spyglass","Signal Spyglass",Material.SPYGLASS,"Highlights distant movement briefly."),
  EMERGENCY_LANTERN("emergency_lantern","Emergency Lantern",Material.LANTERN,"Temporarily improves vision in the dark."),
  BROKEN_CHRONOMETER("broken_chronometer","Broken Chronometer",Material.CLOCK,"Disorients you as time skips."),
  MOTION_DETECTOR("motion_detector","Motion Detector",Material.TRIPWIRE_HOOK,"Detects nearby moving life, not its identity."),
  RESONANCE_HORN("resonance_horn","Resonance Horn",Material.GOAT_HORN,"A pulse reveals entities, but may attract attention."),
  EMERGENCY_BARRIER("emergency_barrier","Emergency Barrier",Material.SHIELD,"Raises a short-lived barrier around you."),
  NULL_WARD("null_ward","Null Ward",Material.TOTEM_OF_UNDYING,"Absorbs one incoming hit."),
  FIELD_MEDKIT("field_medkit","Field Medkit",Material.HONEY_BOTTLE,"Restores health and clears harmful effects."),
  STABILIZER_INJECTION("stabilizer_injection","Stabilizer Injection",Material.POTION,"Reduces tension and clears disorientation."),
  EMERGENCY_RATIONS("emergency_rations","Emergency Rations",Material.COOKED_BEEF,"Restores hunger and saturation."),
  SEDATIVE("sedative","Sedative",Material.FERMENTED_SPIDER_EYE,"Slows you while briefly restoring health."),
  INVESTIGATION_JOURNAL("investigation_journal","Investigation Journal",Material.WRITABLE_BOOK,"Reviews your chapter and recorded clues."),
  EVIDENCE_SCANNER("evidence_scanner","Evidence Scanner",Material.AMETHYST_SHARD,"Searches nearby blocks for evidence signatures."),
  ECHO_RECORDER("echo_recorder","Echo Recorder",Material.ECHO_SHARD,"Plays a resonance and listens for an answer."),
  ANOMALY_MAP("anomaly_map","Anomaly Map",Material.MAP,"Reports the nearest registered structure coordinates."),
  PORTABLE_STORAGE("portable_storage","Portable Storage Device",Material.BARREL,"Opens your own persistent 27-slot storage."),
  SIGNAL_BEACON("signal_beacon","Signal Beacon",Material.LODESTONE,"Marks your location with a visible signal."),
  TRIPWIRE_ALARM("tripwire_alarm","Tripwire Alarm",Material.TRIPWIRE_HOOK,"Warns when movement approaches your chosen spot."),
  EMERGENCY_FLARE("emergency_flare","Emergency Flare",Material.FIREWORK_STAR,"Creates a bright signal and reveals nearby movement."),
  RESONANCE_CORE("resonance_core","Resonance Core",Material.HEART_OF_THE_SEA,"Reveals nearby entities and evidence."),
  DEATH_ECHO("death_echo","Death Echo",Material.RECOVERY_COMPASS,"Recalls your last death coordinates in this world."),
  NULL_FRAGMENT("null_fragment","Null Fragment",Material.CRYING_OBSIDIAN,"Using it increases Null's attention."),
  LAST_RESORT_DEVICE("last_resort_device","Last Resort Device",Material.ENDER_PEARL,"One-use emergency return to the entrance."),
  SPEED_BOOST("speed_boost","Adrenaline Injector",Material.SUGAR,"Grants Speed II for 18 seconds."),
  CHARM_OF_THE_FORGOTTEN("charm_of_the_forgotten","Charm of the Forgotten",Material.AMETHYST_SHARD,"A surviving fragment of the world's erased creator. Speed III while held. Mining Fatigue II and Weakness II linger for 30 seconds after unequipping."),
  RECONSTRUCTOR("reconstructor","Reconstructor",Material.RESPAWN_ANCHOR,"One-use personal respawn anchor. Right-click to bind your current location; the device destroys itself.");
  public final String id,title,lore; public final Material material;
  Type(String id,String title,Material material,String lore){this.id=id;this.title=title;this.material=material;this.lore=lore;}
  public static Optional<Type> byId(String id){return Arrays.stream(values()).filter(t->t.id.equals(id)).findFirst();}
 }
 private final UnderTheBedrockPlugin plugin;private final VoidWorldManager worlds;private final NamespacedKey itemKey,wardKey,evidenceKey;
 private final File cooldownFile,storageFile,deathFile,respawnFile;private final YamlConfiguration cooldowns,storage,deaths,respawns;private final NamespacedKey recipeKey;
 private final Map<UUID,Inventory> open=new HashMap<>();private final Map<UUID,Location> alarms=new HashMap<>();private BukkitTask alarmTask,charmEffectTask;
 public CustomItemManager(UnderTheBedrockPlugin p,VoidWorldManager w){plugin=p;worlds=w;itemKey=new NamespacedKey(p,"underbedrock-item");wardKey=new NamespacedKey(p,"null-ward");evidenceKey=new NamespacedKey(p,"evidence-clue");cooldownFile=file("item-cooldowns.yml");storageFile=file("portable-storage.yml");deathFile=file("death-echoes.yml");respawnFile=file("reconstructor-spawns.yml");recipeKey=new NamespacedKey(p,"reconstructor");cooldowns=YamlConfiguration.loadConfiguration(cooldownFile);storage=YamlConfiguration.loadConfiguration(storageFile);deaths=YamlConfiguration.loadConfiguration(deathFile);respawns=YamlConfiguration.loadConfiguration(respawnFile);}
 private File file(String n){return new File(plugin.getDataFolder(),n);}
 public void start(){plugin.getServer().getPluginManager().registerEvents(this,plugin);registerReconstructorRecipe();alarmTask=plugin.getServer().getScheduler().runTaskTimer(plugin,this::tickAlarms,20,20);charmEffectTask=plugin.getServer().getScheduler().runTaskTimer(plugin,this::applyForgottenCharmEffects,10,10);}
 public void stop(){if(alarmTask!=null)alarmTask.cancel();if(charmEffectTask!=null)charmEffectTask.cancel();for(var e:open.entrySet())saveStorage(e.getKey(),e.getValue());open.clear();save(cooldowns,cooldownFile);save(storage,storageFile);save(deaths,deathFile);save(respawns,respawnFile);Bukkit.removeRecipe(recipeKey);}
 public List<Type> allItems(){return List.of(Type.values());}
 public ItemStack createItem(Type type){ItemStack s=new ItemStack(type.material);ItemMeta m=s.getItemMeta();m.setDisplayName(ChatColor.GOLD+type.title);m.setLore(List.of(ChatColor.GRAY+type.lore,ChatColor.DARK_GRAY+"Under The Bedrock equipment",ChatColor.DARK_GRAY+"Right-click to use"));m.getPersistentDataContainer().set(itemKey,PersistentDataType.STRING,type.id);s.setItemMeta(m);return s;}
 public boolean isCustomItem(ItemStack s){return s!=null&&s.hasItemMeta()&&s.getItemMeta().getPersistentDataContainer().has(itemKey,PersistentDataType.STRING);}
 @EventHandler(ignoreCancelled=true) public void use(PlayerInteractEvent e){
  if(e.getAction()!=Action.RIGHT_CLICK_AIR&&e.getAction()!=Action.RIGHT_CLICK_BLOCK)return;ItemStack stack=e.getItem();if(!isCustomItem(stack))return;
  String id=stack.getItemMeta().getPersistentDataContainer().get(itemKey,PersistentDataType.STRING);Type t=Type.byId(id).orElse(null);if(t==null)return;Player p=e.getPlayer();
  if(!worlds.isActive()||!p.getWorld().equals(worlds.getWorld())){p.sendMessage(ChatColor.DARK_GRAY+"This equipment only responds beneath the bedrock.");return;}
  e.setCancelled(true);if(t==Type.RECONSTRUCTOR){bindRespawn(p);consume(p,stack,e.getHand());return;}if(t==Type.PORTABLE_STORAGE){openStorage(p);return;}if(t==Type.NULL_WARD){ward(p);return;}if(t==Type.TRIPWIRE_ALARM){alarm(p);return;}if(!ready(p,t))return;
  boolean consume=false;
  switch(t){
   case DISTORTED_COMPASS->compass(p);
   case SIGNAL_SPYGLASS->scan(p,48,8);
   case EMERGENCY_LANTERN->{p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,440,0));p.sendMessage(ChatColor.YELLOW+"The dark becomes briefly legible.");}
   case BROKEN_CHRONOMETER->{p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,30,0));p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_BASS,.8f,.45f);p.sendTitle(ChatColor.DARK_GRAY+"00:00:??",ChatColor.GRAY+"The second hand moves backward.",5,35,10);}
   case MOTION_DETECTOR->motion(p);
   case RESONANCE_HORN->{p.playSound(p.getLocation(),Sound.ENTITY_WARDEN_HEARTBEAT,1,.7f);scan(p,32,6);provoke(p,2);}
   case EMERGENCY_BARRIER->barrier(p);
   case FIELD_MEDKIT->{heal(p,6);clear(p,PotionEffectType.POISON,PotionEffectType.WITHER,PotionEffectType.SLOWNESS);p.sendMessage(ChatColor.GREEN+"You patch yourself up.");}
   case STABILIZER_INJECTION->{clear(p,PotionEffectType.BLINDNESS,PotionEffectType.DARKNESS,PotionEffectType.NAUSEA);calm(p,18,12);p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,100,0));p.sendMessage(ChatColor.AQUA+"Your breathing steadies.");}
   case EMERGENCY_RATIONS->{p.setFoodLevel(20);p.setSaturation(12);p.sendMessage(ChatColor.GREEN+"You eat the emergency rations.");}
   case SEDATIVE->{p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,100,0));p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,100,1));calm(p,10,5);p.sendMessage(ChatColor.GRAY+"Your pulse slows. Your legs feel heavy.");}
   case INVESTIGATION_JOURNAL->journal(p);
   case EVIDENCE_SCANNER->evidenceScan(p);
   case ECHO_RECORDER->{p.playSound(p.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_RESONATE,1,.55f);plugin.getServer().getScheduler().runTaskLater(plugin,()->{if(p.isOnline())p.playSound(p.getLocation().subtract(p.getLocation().getDirection().multiply(4)),Sound.BLOCK_AMETHYST_BLOCK_RESONATE,.65f,.42f);},24);p.sendMessage(ChatColor.DARK_GRAY+"The recorder catches a second pulse.");}
   case ANOMALY_MAP->anomalyMap(p);
   case SIGNAL_BEACON->signal(p);
   case EMERGENCY_FLARE->{p.getWorld().spawnParticle(Particle.FLAME,p.getLocation().add(0,1,0),70,.7,.9,.7,.04);p.playSound(p.getLocation(),Sound.ENTITY_FIREWORK_ROCKET_LAUNCH,1,.8f);scan(p,24,5);}
   case RESONANCE_CORE->{scan(p,64,12);evidenceScan(p);p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,240,0));}
   case DEATH_ECHO->deathEcho(p);
   case NULL_FRAGMENT->{provoke(p,12);p.sendMessage(ChatColor.DARK_PURPLE+"The fragment warms in your hand. Something notices.");}
   case LAST_RESORT_DEVICE->{p.teleport(worlds.getWorld().getSpawnLocation().clone().add(.5,0,.5));clear(p,PotionEffectType.BLINDNESS,PotionEffectType.DARKNESS,PotionEffectType.SLOWNESS);p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,100,1));p.sendMessage(ChatColor.GREEN+"You are back at the entrance. The device crumbles.");consume=true;}
   case SPEED_BOOST->{p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,360,1));p.playSound(p.getLocation(),Sound.ENTITY_PLAYER_ATTACK_SWEEP,.7f,1.6f);p.sendMessage(ChatColor.GREEN+"Adrenaline surges through you.");}
   default->{}
  }
  cooldown(p,t);if(consume)consume(p,stack,e.getHand());
 }
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(EntityDamageEvent e){if(!(e.getEntity() instanceof Player p)||!p.getPersistentDataContainer().has(wardKey,PersistentDataType.BYTE))return;p.getPersistentDataContainer().remove(wardKey);e.setCancelled(true);p.playSound(p.getLocation(),Sound.ITEM_TOTEM_USE,1,.7f);p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,p.getLocation().add(0,1,0),30,.5,.7,.5,.1);p.sendMessage(ChatColor.LIGHT_PURPLE+"The Null Ward fractures and absorbs the hit.");}
 @EventHandler public void death(PlayerDeathEvent e){Player p=e.getEntity();if(!worlds.isActive()||!p.getWorld().equals(worlds.getWorld()))return;Location l=p.getLocation();String k=p.getUniqueId().toString();deaths.set(k+".world",l.getWorld().getName());deaths.set(k+".x",l.getBlockX());deaths.set(k+".y",l.getBlockY());deaths.set(k+".z",l.getBlockZ());save(deaths,deathFile);}
 @EventHandler(priority=EventPriority.HIGHEST) public void respawn(PlayerRespawnEvent e){String key=e.getPlayer().getUniqueId().toString();World bedrock=worlds.getWorld();if(bedrock==null||!bedrock.getName().equals(deaths.getString(key+".world")))return;Location saved=loadRespawn(key);e.setRespawnLocation(saved==null?worlds.getEntranceLocation():saved);}
 @EventHandler public void storageClose(InventoryCloseEvent e){if(e.getInventory().getHolder() instanceof Holder h){saveStorage(h.owner,e.getInventory());open.remove(h.owner);}}
 private void registerReconstructorRecipe(){ShapedRecipe recipe=new ShapedRecipe(recipeKey,createItem(Type.RECONSTRUCTOR));recipe.shape(" D ","LSM"," B ");recipe.setIngredient('D',new RecipeChoice.ExactChoice(createItem(Type.DISTORTED_COMPASS)));recipe.setIngredient('L',new RecipeChoice.ExactChoice(createItem(Type.EMERGENCY_LANTERN)));recipe.setIngredient('S',new RecipeChoice.ExactChoice(createItem(Type.STABILIZER_INJECTION)));recipe.setIngredient('M',new RecipeChoice.ExactChoice(createItem(Type.FIELD_MEDKIT)));recipe.setIngredient('B',new RecipeChoice.ExactChoice(createItem(Type.SIGNAL_BEACON)));Bukkit.removeRecipe(recipeKey);Bukkit.addRecipe(recipe);}
 private void bindRespawn(Player p){Location l=p.getLocation().clone();String k=p.getUniqueId().toString();respawns.set(k+".world",l.getWorld().getName());respawns.set(k+".x",l.getX());respawns.set(k+".y",l.getY());respawns.set(k+".z",l.getZ());respawns.set(k+".yaw",l.getYaw());respawns.set(k+".pitch",l.getPitch());save(respawns,respawnFile);p.playSound(l,Sound.BLOCK_RESPAWN_ANCHOR_CHARGE,1,.8f);p.getWorld().spawnParticle(Particle.REVERSE_PORTAL,l.clone().add(0,1,0),35,.45,.7,.45,.04);p.sendMessage(ChatColor.LIGHT_PURPLE+"RECONSTRUCTOR: "+ChatColor.WHITE+"This location is now your respawn point. The device collapses into dust.");}
 private Location loadRespawn(String key){String worldName=respawns.getString(key+".world");if(worldName==null)return null;World world=Bukkit.getWorld(worldName);if(world==null)return null;return new Location(world,respawns.getDouble(key+".x"),respawns.getDouble(key+".y"),respawns.getDouble(key+".z"),(float)respawns.getDouble(key+".yaw"),(float)respawns.getDouble(key+".pitch"));}
 private boolean ready(Player p,Type t){long until=cooldowns.getLong(p.getUniqueId()+"."+t.id);if(until>System.currentTimeMillis()){p.sendMessage(ChatColor.DARK_GRAY+"That device needs "+Math.max(1,(until-System.currentTimeMillis()+999)/1000)+"s to recharge.");return false;}return true;}
 private void applyForgottenCharmEffects(){
  if(!worlds.isActive()||worlds.getWorld()==null)return;
  for(Player p:worlds.getWorld().getPlayers())if(holdsCharm(p)){
   // Speed is the benefit; the drawbacks persist after the charm is unequipped.
   p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,25,2,false,false,false));
   // Refresh for 30 seconds while equipped; the remaining duration counts down after unequipping.
   p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE,600,1,false,false,false));
   p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,600,1,false,false,false));
  }
 }
 private boolean holdsCharm(Player p){return isType(p.getInventory().getItemInMainHand(),Type.CHARM_OF_THE_FORGOTTEN)||isType(p.getInventory().getItemInOffHand(),Type.CHARM_OF_THE_FORGOTTEN);}
 private boolean isType(ItemStack stack,Type type){
  if(!isCustomItem(stack))return false;
  return type.id.equals(stack.getItemMeta().getPersistentDataContainer().get(itemKey,PersistentDataType.STRING));
 }
 public ItemStack randomLootItem(Random random){
  if(random.nextInt(100)==0)return createItem(Type.CHARM_OF_THE_FORGOTTEN);
  Type[] common=Arrays.stream(Type.values()).filter(t->t!=Type.CHARM_OF_THE_FORGOTTEN&&t!=Type.RECONSTRUCTOR).toArray(Type[]::new);
  return createItem(common[random.nextInt(common.length)]);
 }
 private long defaultCd(Type t){return switch(t){case FIELD_MEDKIT,STABILIZER_INJECTION,EMERGENCY_RATIONS,SEDATIVE->45;case SPEED_BOOST->35;case EMERGENCY_BARRIER,NULL_WARD,LAST_RESORT_DEVICE->90;case DISTORTED_COMPASS,ANOMALY_MAP,INVESTIGATION_JOURNAL,DEATH_ECHO->15;default->25;};}
 private void cooldown(Player p,Type t){long seconds=plugin.getConfig().getLong("horror.items.cooldowns."+t.id,defaultCd(t));cooldowns.set(p.getUniqueId()+"."+t.id,System.currentTimeMillis()+Math.max(1,seconds)*1000);save(cooldowns,cooldownFile);}
 private void ward(Player p){if(p.getPersistentDataContainer().has(wardKey,PersistentDataType.BYTE)){p.sendMessage(ChatColor.GRAY+"A Null Ward is already armed.");return;}if(!ready(p,Type.NULL_WARD))return;p.getPersistentDataContainer().set(wardKey,PersistentDataType.BYTE,(byte)1);cooldown(p,Type.NULL_WARD);p.sendMessage(ChatColor.LIGHT_PURPLE+"The ward hums once. It will absorb the next hit.");}
 private void compass(Player p){Location target=nearest(p.getLocation());if(target==null)target=worlds.getWorld().getSpawnLocation();if(Math.random()<.22){double a=Math.random()*Math.PI*2;target=target.clone().add(Math.cos(a)*160,0,Math.sin(a)*160);}p.setCompassTarget(target);p.sendMessage(ChatColor.GOLD+"The needle settles: "+target.getBlockX()+", "+target.getBlockZ()+".");}
 private void motion(Player p){int n=0;Entity nearest=null;double d=Double.MAX_VALUE;for(Entity e:p.getNearbyEntities(18,10,18))if(e instanceof LivingEntity&&!(e instanceof ArmorStand)){n++;double q=e.getLocation().distanceSquared(p.getLocation());if(q<d){d=q;nearest=e;}}p.sendMessage(nearest==null?ChatColor.GREEN+"MOTION: no signal within 18 blocks.":ChatColor.YELLOW+"MOTION: "+n+" signal(s), nearest approximately "+Math.round(Math.sqrt(d))+" blocks away.");}
 private void scan(Player p,int radius,int seconds){int n=0;for(Entity e:p.getNearbyEntities(radius,radius/2.0,radius))if(e instanceof LivingEntity&&!(e instanceof ArmorStand)&&!(e instanceof Player)){e.setGlowing(true);n++;plugin.getServer().getScheduler().runTaskLater(plugin,()->{if(e.isValid())e.setGlowing(false);},seconds*20L);}p.getWorld().spawnParticle(Particle.END_ROD,p.getLocation().add(0,1,0),22,.6,.6,.6,.04);p.sendMessage(ChatColor.AQUA+"Signal pulse: "+n+" nearby contact(s) marked briefly.");}
 private void barrier(Player p){Location base=p.getLocation().getBlock().getLocation();List<Block> placed=new ArrayList<>();for(int y=0;y<2;y++)for(int i=-2;i<=2;i++)for(int[] c:new int[][]{{-2,i},{2,i},{i,-2},{i,2}}){Block b=base.clone().add(c[0],y,c[1]).getBlock();if(b.getType().isAir()){b.setType(Material.BARRIER,false);placed.add(b);}}p.sendMessage(ChatColor.AQUA+"A barrier locks into place for five seconds.");plugin.getServer().getScheduler().runTaskLater(plugin,()->placed.forEach(b->{if(b.getType()==Material.BARRIER)b.setType(Material.AIR,false);}),100);}
 private void heal(Player p,double n){p.setHealth(Math.min(p.getMaxHealth(),p.getHealth()+n));}
 private void clear(Player p,PotionEffectType...ts){for(PotionEffectType t:ts)p.removePotionEffect(t);}
 private void calm(Player p,double t,double v){PlayerHorrorMemory m=plugin.getOccurrenceDirector().memory(p.getUniqueId());m.decayTension(t);m.calmProvocation(v);plugin.getOccurrenceDirector().saveMemory(p.getUniqueId());}
 private void provoke(Player p,double n){PlayerHorrorMemory m=plugin.getOccurrenceDirector().memory(p.getUniqueId());m.provoke(n);m.addTension(n*.35);plugin.getOccurrenceDirector().saveMemory(p.getUniqueId());}
 private void journal(Player p){PlayerHorrorMemory m=plugin.getOccurrenceDirector().memory(p.getUniqueId());p.sendMessage(ChatColor.DARK_GRAY+"§lFIELD JOURNAL");p.sendMessage(ChatColor.GRAY+"Chapter: "+ChatColor.WHITE+StoryChapter.forPlayer(m).name().replace('_',' '));p.sendMessage(ChatColor.GRAY+"Evidence: "+ChatColor.WHITE+m.discoveredClueCount()+"/"+ClueDefinition.values().length);p.sendMessage(ChatColor.GRAY+"Last clue: "+ChatColor.WHITE+ClueDefinition.byId(m.lastClueId()).map(ClueDefinition::title).orElse("none"));p.sendMessage(ChatColor.GRAY+"Distance recorded: "+ChatColor.WHITE+m.distanceTravelled()+" blocks");}
 private void evidenceScan(Player p){int n=0;Location c=p.getLocation();for(int x=-10;x<=10;x++)for(int y=-5;y<=5;y++)for(int z=-10;z<=10;z++){Block b=c.getWorld().getBlockAt(c.getBlockX()+x,c.getBlockY()+y,c.getBlockZ()+z);if(b.getState() instanceof Sign s&&s.getPersistentDataContainer().has(evidenceKey,PersistentDataType.STRING)){n++;p.spawnParticle(Particle.END_ROD,b.getLocation().add(.5,1,.5),5,.15,.2,.15,.01);}}p.sendMessage(n==0?ChatColor.GRAY+"No evidence signature detected nearby.":ChatColor.LIGHT_PURPLE+"Scanner detected "+n+" evidence signature(s).");}
 private void anomalyMap(Player p){Location l=nearest(p.getLocation());if(l==null){p.sendMessage(ChatColor.GRAY+"No registered anomaly is on the map.");return;}p.setCompassTarget(l);p.sendMessage(ChatColor.GOLD+"Nearest structure: "+ChatColor.WHITE+"X "+l.getBlockX()+" Y "+l.getBlockY()+" Z "+l.getBlockZ());}
 private Location nearest(Location from){YamlConfiguration y=YamlConfiguration.loadConfiguration(file("structures.yml"));ConfigurationSection s=y.getConfigurationSection("sites");if(s==null)return null;Location best=null;double dist=Double.MAX_VALUE;for(String k:s.getKeys(false)){ConfigurationSection a=s.getConfigurationSection(k);if(a==null)continue;Location l=new Location(from.getWorld(),a.getInt("x"),plugin.getConfig().getInt("world.floor-y",64)+1,a.getInt("z"));double d=l.distanceSquared(from);if(d<dist){dist=d;best=l;}}return best;}
 private void signal(Player p){Location l=p.getLocation();p.getWorld().spawnParticle(Particle.END_ROD,l.clone().add(0,1,0),50,.5,1,.5,.08);p.playSound(l,Sound.BLOCK_BEACON_POWER_SELECT,1,.8f);p.sendMessage(ChatColor.GOLD+"BEACON: "+ChatColor.WHITE+"X "+l.getBlockX()+" Y "+l.getBlockY()+" Z "+l.getBlockZ());}
 private void deathEcho(Player p){String k=p.getUniqueId().toString();if(!deaths.contains(k+".x")){p.sendMessage(ChatColor.GRAY+"No death has been recorded in this place.");return;}p.sendMessage(ChatColor.DARK_PURPLE+"LAST DEATH: "+ChatColor.WHITE+"X "+deaths.getInt(k+".x")+" Y "+deaths.getInt(k+".y")+" Z "+deaths.getInt(k+".z"));}
 private void alarm(Player p){if(!ready(p,Type.TRIPWIRE_ALARM))return;alarms.put(p.getUniqueId(),p.getLocation().clone());cooldown(p,Type.TRIPWIRE_ALARM);p.sendMessage(ChatColor.YELLOW+"Alarm armed here. It will warn you when movement approaches.");}
 private void tickAlarms(){Iterator<Map.Entry<UUID,Location>> it=alarms.entrySet().iterator();while(it.hasNext()){var a=it.next();Player owner=Bukkit.getPlayer(a.getKey());Location l=a.getValue();if(owner==null||!owner.isOnline()||!owner.getWorld().equals(l.getWorld())){it.remove();continue;}if(!l.getWorld().isChunkLoaded(Math.floorDiv(l.getBlockX(),16),Math.floorDiv(l.getBlockZ(),16)))continue;for(Entity e:l.getWorld().getNearbyEntities(l,8,5,8))if(e instanceof LivingEntity&&!(e instanceof ArmorStand)&&!e.getUniqueId().equals(owner.getUniqueId())){owner.playSound(owner.getLocation(),Sound.BLOCK_TRIPWIRE_CLICK_ON,1,.65f);owner.sendMessage(ChatColor.RED+"TRIPWIRE: movement detected near your alarm.");it.remove();break;}}}
 private void openStorage(Player p){Inventory inv=open.get(p.getUniqueId());if(inv==null){Holder h=new Holder(p.getUniqueId());inv=Bukkit.createInventory(h,27,ChatColor.DARK_GRAY+"Portable Storage");h.inv=inv;List<?> contents=storage.getList(p.getUniqueId().toString(),List.of());for(int i=0;i<Math.min(27,contents.size());i++)if(contents.get(i) instanceof ItemStack item)inv.setItem(i,item);open.put(p.getUniqueId(),inv);}p.openInventory(inv);}
 private void saveStorage(UUID id,Inventory inv){storage.set(id.toString(),new ArrayList<>(Arrays.asList(inv.getContents())));save(storage,storageFile);}
 private void consume(Player p,ItemStack s,EquipmentSlot hand){if(s.getAmount()<=1){if(hand==EquipmentSlot.OFF_HAND)p.getInventory().setItemInOffHand(null);else p.getInventory().setItemInMainHand(null);}else s.setAmount(s.getAmount()-1);}
 private void save(YamlConfiguration y,File f){try{if(!f.getParentFile().exists())f.getParentFile().mkdirs();y.save(f);}catch(IOException e){plugin.getLogger().severe("Could not save "+f.getName()+": "+e.getMessage());}}
 private static final class Holder implements InventoryHolder{final UUID owner;Inventory inv;Holder(UUID id){owner=id;}public Inventory getInventory(){return inv;}}
}
