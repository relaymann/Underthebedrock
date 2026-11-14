package dev.underthebedrock.horror;
import java.util.Arrays;import java.util.List;import java.util.Optional;import java.util.Random;
/** Ordered evidence chain: the first six clues investigate the anomaly; the final four uncover the Forgotten. */
public enum ClueDefinition {
 FIRST_SIGNAL("first_signal",768,null,"THE FIRST SIGNAL","It is not a recording. It is a reply."),
 FALSE_EXIT("false_exit",1536,"first_signal","THE FALSE EXIT","The path ended here before you arrived."),
 THREE_MARKS("three_marks",2560,"false_exit","THREE MARKS","One mark for the place. One for the time. One for you."),
 NAME_FRAGMENT("name_fragment",4096,"three_marks","NAME FRAGMENT","The name has been scratched out from the inside."),
 RECORDING("recording",6144,"name_fragment","THE RECORDING","Listen between the words. Someone is counting."),
 FINAL_ADDRESS("final_address",8192,"recording","THE ADDRESS","You were not meant to find the place. You were meant to remember it."),
 CREATOR_MARK("creator_mark",9216,"final_address","THE FIRST HAND","The first stone remembers a hand the records do not. The mark beneath it is older than Null."),
 WORLD_BEFORE("world_before",10240,"creator_mark","THE WORLD BEFORE","I made the boundaries. I gave this place its shape. I did not make what crossed into it."),
 REMOVAL_ORDER("removal_order",12288,"world_before","THE REMOVAL ORDER","If this reaches you, the command failed. I tried to remove Null. It learned what I was doing."),
 ERASURE("erasure",16384,"removal_order","THE LAST WITNESS","It did not kill me. It removed my name, my work, and every memory of my existence. Null erased me first. That is why you call me the Forgotten.");
 private final String id;private final int minimumDistance;private final String prerequisite,title,text;
 ClueDefinition(String id,int distance,String prerequisite,String title,String text){this.id=id;minimumDistance=distance;this.prerequisite=prerequisite;this.title=title;this.text=text;}
 public String id(){return id;}public int minimumDistance(){return minimumDistance;}public String prerequisite(){return prerequisite;}public String title(){return title;}public String text(){return text;}public boolean availableAt(double distance){return distance>=minimumDistance;}public boolean prerequisiteMet(PlayerHorrorMemory m){return prerequisite==null||m.hasClue(prerequisite);}
 public static Optional<ClueDefinition> forDistance(double d){return Arrays.stream(values()).filter(c->c.availableAt(d)).reduce((a,b)->b);}
 /** Deeper regions can contain earlier evidence too, so a missed clue never soft-locks a run. */
 public static Optional<ClueDefinition> forSite(double d,long seed){List<ClueDefinition> eligible=Arrays.stream(values()).filter(c->c.availableAt(d)).toList();if(eligible.isEmpty())return Optional.empty();return Optional.of(eligible.get(new Random(seed^0x6A09E667F3BCC909L).nextInt(eligible.size())));}
 public static Optional<ClueDefinition> byId(String id){return Arrays.stream(values()).filter(c->c.id.equalsIgnoreCase(id)).findFirst();}
}
