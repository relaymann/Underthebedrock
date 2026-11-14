package dev.underthebedrock.horror;
/** Player-specific chapter estimate derived from persisted exploration and encounter history. */
public enum StoryChapter {
 ARRIVAL, ANOMALIES, SUSPICION, INVESTIGATION, NULL_INFLUENCE, DEEP_EXPLORATION;
 public static StoryChapter forPlayer(PlayerHorrorMemory m){
  long d=m.distanceTravelled(),e=m.encounters(); double p=m.provocation();
  if(d>=30000||(d>=12000&&e>=8))return DEEP_EXPLORATION;
  if(p>=35||(d>=6000&&e>=5))return NULL_INFLUENCE;
  if(d>=3000||e>=12)return INVESTIGATION;
  if(d>=1000||e>=5)return SUSPICION;
  if(d>=250||e>=1)return ANOMALIES;
  return ARRIVAL;
 }
}