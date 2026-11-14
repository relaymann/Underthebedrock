package dev.underthebedrock.horror;
import java.util.Arrays;import java.util.List;import java.util.Optional;import java.util.Random;
/** Ordered evidence chain gated by exploration depth and its predecessor. */
public enum ClueDefinition {
 FIRST_SIGNAL("first_signal",768,null,"THE FIRST SIGNAL","It is not a recording. It is a reply."),
 FALSE_EXIT("false_exit",1536,"first_signal","THE FALSE EXIT","The path ended here before you arrived."),
 THREE_MARKS("three_marks",2560,"false_exit","THREE MARKS","One mark for the place. One for the time. One for you."),
 NAME_FRAGMENT("name_fragment",4096,"three_marks","NAME FRAGMENT","The name has been scratched out from the inside."),
 RECORDING("recording",6144,"name_fragment","THE RECORDING","Listen between the words. Someone is counting."),
 FINAL_ADDRESS("final_address",8192,"recording","THE ADDRESS","You were not meant to find the place. You were meant to remember it.");
 private final String id;private final int minimumDistance;private final String prerequisite,title,text;
 ClueDefinition(String id,int distance,String prerequisite,String title,String text){this.id=id;minimumDistance=distance;this.prerequisite=prerequisite;this.title=title;this.text=text;}
 public String id(){return id;}public int minimumDistance(){return minimumDistance;}public String prerequisite(){return prerequisite;}public String title(){return title;}public String text(){return text;}public boolean availableAt(double distance){return distance>=minimumDistance;}public boolean prerequisiteMet(PlayerHorrorMemory m){return prerequisite==null||m.hasClue(prerequisite);}
 public static Optional<ClueDefinition> forDistance(double d){return Arrays.stream(values()).filter(c->c.availableAt(d)).reduce((a,b)->b);}
 /** Deeper regions can contain earlier evidence too, so a missed clue never soft-locks a run. */
 public static Optional<ClueDefinition> forSite(double d,long seed){List<ClueDefinition> eligible=Arrays.stream(values()).filter(c->c.availableAt(d)).toList();if(eligible.isEmpty())return Optional.empty();return Optional.of(eligible.get(new Random(seed^0x6A09E667F3BCC909L).nextInt(eligible.size())));}
 public static Optional<ClueDefinition> byId(String id){return Arrays.stream(values()).filter(c->c.id.equalsIgnoreCase(id)).findFirst();}
}