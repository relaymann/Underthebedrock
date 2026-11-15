package dev.underthebedrock;
import dev.underthebedrock.command.*;import dev.underthebedrock.horror.*;import dev.underthebedrock.integration.IntegrationManager;import dev.underthebedrock.world.VoidWorldManager;import dev.underthebedrock.world.StructureManager;import dev.underthebedrock.world.SupplyDropManager;import dev.underthebedrock.item.CustomItemManager;import dev.underthebedrock.team.TeamManager;import dev.underthebedrock.base.BaseManager;import org.bukkit.command.PluginCommand;import org.bukkit.plugin.java.JavaPlugin;import java.util.Objects;
public final class UnderTheBedrockPlugin extends JavaPlugin {
 private VoidWorldManager worlds;private IntegrationManager integrations;private OccurrenceDirector occurrences;private HorrorEntityController entities;private ClueManager clues;private StructureManager structures;private SupplyDropManager supplyDrops;private CustomItemManager customItems;private LoreManager lore;private CivilizationManager civilization;private WorldEntryListener worldEntry;private HorrorEventDirector horrorEvents;private FacilityManager facilities;private TeamManager teams;private BaseManager bases;
 @Override public void onEnable(){saveDefaultConfig();if(!FullResetService.processPending(this)){getServer().getPluginManager().disablePlugin(this);return;}applyExplorationPacingDefaults();integrations=new IntegrationManager(this);if(!integrations.initialize()){getServer().getPluginManager().disablePlugin(this);return;}worlds=new VoidWorldManager(this);PlayerHorrorMemoryStore memoryStore=new PlayerHorrorMemoryStore(this);occurrences=new OccurrenceDirector(this,worlds,memoryStore);entities=new HorrorEntityController(this,worlds,occurrences,integrations.betterModel());clues=new ClueManager(this,worlds,occurrences);customItems=new CustomItemManager(this,worlds);teams=new TeamManager(this);bases=new BaseManager(this,worlds,teams);structures=new StructureManager(this,worlds);supplyDrops=new SupplyDropManager(this,worlds);lore=new LoreManager(this);lore.start();civilization=new CivilizationManager(this);worldEntry=new WorldEntryListener(this,worlds,customItems);getServer().getPluginManager().registerEvents(worldEntry,this);worldEntry.start();facilities=new FacilityManager(this);facilities.start();if(getConfig().getBoolean("horror.civilization.enabled",true))civilization.start();
  if(getConfig().getBoolean("horror.director.enabled",true)){occurrences.start();if(getConfig().getBoolean("horror.entities.enabled",true))entities.start();if(getConfig().getBoolean("horror.clues.enabled",true))clues.start();}
  horrorEvents=new HorrorEventDirector(this);if(getConfig().getBoolean("horror.events.enabled",true))horrorEvents.start();
  if(getConfig().getBoolean("horror.items.enabled",true))customItems.start();teams.start();bases.start();register("team",teams);register("base",bases);register("enter",new EnterCommand(worldEntry));register("underthebedrock",new UnderTheBedrockCommand(this,worlds,integrations,worldEntry));register("evidence",new EvidenceCommand(occurrences));
  if(getConfig().getBoolean("horror.structures.enabled",true))structures.start();if(getConfig().getBoolean("horror.supply-drops.enabled",true))supplyDrops.start();
  if(getConfig().getBoolean("world.auto-start",true)||getConfig().getBoolean("world.enabled-on-startup",false)){if(worlds.start())getServer().getScheduler().runTask(this,worldEntry::routeOnlinePlayers);else getLogger().severe("Could not initialize the configured horror world.");}
  getLogger().info("Under The Bedrock enabled; auto-start="+worlds.isActive()+", world lockdown="+getConfig().getBoolean("world.lock-to-experience",true)+".");}
 private void applyExplorationPacingDefaults(){
  if(!getConfig().getBoolean("horror.exploration-pacing-v3",false)){
   getConfig().set("horror.exploration-pacing-v3",true);
   getConfig().set("horror.director.check-interval-seconds",4);
   getConfig().set("horror.director.minimum-occurrence-gap-seconds",10);
   getConfig().set("horror.director.severe-occurrence-cooldown-minutes",1);
   getConfig().set("horror.director.base-roll-chance",0.62);
   getConfig().set("horror.structures.site-cell-size-blocks",512);
   getConfig().set("horror.structures.cell-generation-chance",0.82);
   getConfig().set("horror.structures.spawn-check-radius-blocks",160);
   getConfig().set("horror.structures.scan-interval-ticks",40);
   getConfig().set("horror.civilization.cell-generation-chance",0.58);
   getConfig().set("horror.civilization.scan-interval-ticks",40);
   getConfig().set("horror.clues.scan-interval-ticks",40);
   getConfig().set("horror.entities.safe-radius-blocks",256);
   getConfig().set("horror.entities.full-threat-distance-blocks",3072);
   getConfig().set("horror.entities.maximum-chance-per-check",0.18);
   getConfig().set("horror.entities.successful-encounter-cooldown-minutes",1);
   getConfig().set("horror.entities.behavior.encounter-check-interval-ticks",20);
   getConfig().set("horror.entities.behavior.minimum-spawn-distance-blocks",48);
   getConfig().set("horror.entities.behavior.maximum-spawn-distance-blocks",96);
   getConfig().set("horror.entities.behavior.observation-range-blocks",128);
   getConfig().set("horror.entities.behavior.despawn-range-blocks",176);
   getConfig().set("horror.events.minimum-cooldown-seconds",75);
   getConfig().set("horror.events.maximum-cooldown-seconds",150);
   getLogger().info("Applied exploration pacing v3.");
  }
  if(getConfig().getBoolean("horror.exploration-pacing-v4",false))return;
  getConfig().set("horror.exploration-pacing-v4",true);
  getConfig().set("horror.director.check-interval-seconds",20);
  getConfig().set("horror.director.minimum-occurrence-gap-seconds",180);
  getConfig().set("horror.director.severe-occurrence-cooldown-minutes",15);
  getConfig().set("horror.director.base-roll-chance",0.10);
  getConfig().set("horror.director.maximum-adaptive-bonus",0.05);
  getConfig().set("horror.events.minimum-cooldown-seconds",480);
  getConfig().set("horror.events.maximum-cooldown-seconds",720);
  getConfig().set("horror.structures.site-cell-size-blocks",1536);
  getConfig().set("horror.structures.cell-generation-chance",0.16);
  getConfig().set("horror.structures.spawn-check-radius-blocks",48);
  getConfig().set("horror.structures.scan-interval-ticks",100);
  getConfig().set("horror.civilization.cell-generation-chance",0.20);
  getConfig().set("horror.civilization.scan-interval-ticks",100);
  getConfig().set("horror.clues.scan-interval-ticks",100);
  getConfig().set("horror.entities.maximum-chance-per-check",0.06);
  getConfig().set("horror.entities.successful-encounter-cooldown-minutes",8);
  getConfig().set("horror.entities.behavior.encounter-check-interval-ticks",40);
  getConfig().set("horror.entities.behavior.minimum-spawn-distance-blocks",64);
  getConfig().set("horror.entities.behavior.maximum-spawn-distance-blocks",128);
  saveConfig();
  getLogger().info("Applied exploration pacing v4: lower structure density and longer horror-event downtime.");
 }
 @Override public void onDisable(){if(worldEntry!=null)worldEntry.stop();if(bases!=null)bases.stop();if(teams!=null)teams.stop();if(facilities!=null)facilities.stop();if(horrorEvents!=null)horrorEvents.stop();if(civilization!=null)civilization.stop();if(customItems!=null)customItems.stop();if(supplyDrops!=null)supplyDrops.stop();if(structures!=null)structures.stop();if(clues!=null)clues.stop();if(entities!=null)entities.stop();if(occurrences!=null)occurrences.stop();}
 public boolean requestFullReset(){return FullResetService.request(this);}
 public OccurrenceDirector getOccurrenceDirector(){return occurrences;}public CustomItemManager getCustomItems(){return customItems;}public LoreManager getLoreManager(){return lore;}public VoidWorldManager getWorlds(){return worlds;}public FacilityManager getFacilityManager(){return facilities;}public BaseManager getBaseManager(){return bases;}public TeamManager getTeamManager(){return teams;}
 private void register(String name,org.bukkit.command.CommandExecutor executor){PluginCommand c=Objects.requireNonNull(getCommand(name),"Missing command: "+name);c.setExecutor(executor);if(executor instanceof org.bukkit.command.TabCompleter tabs)c.setTabCompleter(tabs);}
}
