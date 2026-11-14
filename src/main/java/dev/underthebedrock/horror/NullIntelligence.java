package dev.underthebedrock.horror;
import org.bukkit.entity.Player;
import java.util.Locale;
import java.util.Optional;
/** Contextual private-to-player Null reactions; no external model or network dependency. */
public final class NullIntelligence {
 public Optional<String> respond(Player player,String message,PlayerHorrorMemory m){
  String t=message.toLowerCase(Locale.ROOT); StoryChapter c=StoryChapter.forPlayer(m);
  if(has(t,"where are you","show yourself","come here","null"))return Optional.of(switch(c){
   case ARRIVAL,ANOMALIES->"§8...you are looking in the wrong direction.";
   case SUSPICION,INVESTIGATION->"§8You keep asking as if I owe you an answer.";
   case NULL_INFLUENCE->"§8I was here before you decided to look.";
   case DEEP_EXPLORATION->"§8You finally walked far enough to hear me.";});
  if(has(t,"not afraid","not scared","i know you're there"))return Optional.of(switch(c){
   case ARRIVAL,ANOMALIES->"§8§oYou sound certain.";
   case SUSPICION,INVESTIGATION->"§8Then why did you stop walking?";
   case NULL_INFLUENCE->"§8You said that last time, too.";
   case DEEP_EXPLORATION->"§8Fear is not the thing I am waiting for.";});
  if(has(t,"hate you","stupid","kill you","coward","you can't"))return Optional.of(c.ordinal()>=StoryChapter.NULL_INFLUENCE.ordinal()?"§8§oCareful. I remember how you speak to me.":"§8§oThat was unnecessary.");
  if(has(t,"hello","hi null","anyone there"))return Optional.of(c.ordinal()>=StoryChapter.INVESTIGATION.ordinal()?"§8You already know the answer.":"§8...");
  return Optional.empty();
 }
 private boolean has(String t,String...terms){for(String s:terms)if(t.contains(s))return true;return false;}
}