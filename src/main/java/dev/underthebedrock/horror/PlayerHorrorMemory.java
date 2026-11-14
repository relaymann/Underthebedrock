package dev.underthebedrock.horror;
import java.util.EnumMap;
import java.util.Map;
public final class PlayerHorrorMemory {
 private double tension, provocation; private long lastOccurrenceAt,lastSevereOccurrenceAt,lastChatAt,lastEntityEncounterAt,encounters,distanceTravelled; private String lastOccurrence="none";
 private final Map<EntityArchetype,Integer> entityEncounters=new EnumMap<>(EntityArchetype.class);
 public double tension(){return tension;} public double provocation(){return provocation;} public long lastOccurrenceAt(){return lastOccurrenceAt;} public long lastSevereOccurrenceAt(){return lastSevereOccurrenceAt;} public long lastChatAt(){return lastChatAt;} public long lastEntityEncounterAt(){return lastEntityEncounterAt;} public long encounters(){return encounters;} public long distanceTravelled(){return distanceTravelled;} public String lastOccurrence(){return lastOccurrence;} public Map<EntityArchetype,Integer> entityEncounters(){return entityEncounters;}
 public void restore(double t,double p,long occurrence,long severe,long chat,long entityAt,long count,long distance,String last,Map<EntityArchetype,Integer> entities){tension=clamp(t,0,100);provocation=clamp(p,0,100);lastOccurrenceAt=Math.max(0,occurrence);lastSevereOccurrenceAt=Math.max(0,severe);lastChatAt=Math.max(0,chat);lastEntityEncounterAt=Math.max(0,entityAt);encounters=Math.max(0,count);distanceTravelled=Math.max(0,distance);lastOccurrence=last==null?"none":last;entityEncounters.clear();if(entities!=null)entityEncounters.putAll(entities);}
 public void addTension(double n){tension=clamp(tension+n,0,100);} public void decayTension(double n){tension=clamp(tension-n,0,100);} public void provoke(double n){provocation=clamp(provocation+n,0,100);} public void calmProvocation(double n){provocation=clamp(provocation-n,0,100);} public void addDistance(long n){distanceTravelled+=Math.max(0,n);} public void recordChat(long n){lastChatAt=n;}
 public void recordOccurrence(String id,OccurrenceSeverity severity,long now){lastOccurrence=id;lastOccurrenceAt=now;encounters++;if(severity.rank()>=OccurrenceSeverity.SCARY.rank())lastSevereOccurrenceAt=now;addTension(severity.rank()*1.8+1.5);}
 public void recordEntity(EntityArchetype type,long now){entityEncounters.merge(type,1,Integer::sum);lastEntityEncounterAt=now;addTension(3+type.aggression()*2);}
 public void relax(){decayTension(.08);calmProvocation(.025);}
 private static double clamp(double v,double min,double max){return Math.max(min,Math.min(max,v));}
}
