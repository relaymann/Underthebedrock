package dev.underthebedrock.horror;
/** Progression is derived from durable exploration, encounter, provocation, and evidence history. */
public enum StoryChapter {
 ARRIVAL,ANOMALIES,SUSPICION,INVESTIGATION,NULL_INFLUENCE,DEEP_EXPLORATION;
 public static StoryChapter forPlayer(PlayerHorrorMemory m){long d=m.distanceTravelled(),e=m.encounters();double p=m.provocation();int clues=m.discoveredClueCount();
  if(clues>=6||d>=30000||(d>=12000&&e>=8))return DEEP_EXPLORATION;
  if(clues>=4||p>=35||(d>=6000&&e>=5))return NULL_INFLUENCE;
  if(clues>=2||d>=3000||e>=12)return INVESTIGATION;
  if(clues>=1||d>=1000||e>=5)return SUSPICION;
  if(d>=250||e>=1)return ANOMALIES;return ARRIVAL;}
}