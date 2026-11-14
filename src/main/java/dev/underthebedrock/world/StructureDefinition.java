package dev.underthebedrock.world;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public enum StructureDefinition {
    CAMPSITE("campsite",512,8), CABIN("cabin",768,7), SIGNAL_STATION("signal_station",1024,5),
    OBSERVATION_TOWER("observation_tower",1536,4), NULL_SHRINE("null_shrine",2048,3),
    RAILWAY("railway",2048,2.5), RESEARCH_FACILITY("research_facility",3072,2),
    FORGOTTEN_CHAPEL("forgotten_chapel",3072,1.2), EMPTY_SETTLEMENT("empty_settlement",4096,1.5),
    UNDERGROUND_BUNKER("underground_bunker",4096,1.5), SEALED_GATEWAY("sealed_gateway",5120,.8),
    CONTAINMENT_FACILITY("containment_facility",6144,.45),
    IMPOSSIBLE_CORRIDOR("impossible_corridor",6144,.4), MONOLITH("monolith",8192,.12);

    private final String id; private final int minimumDistance; private final double weight;
    StructureDefinition(String id,int minimumDistance,double weight){this.id=id;this.minimumDistance=minimumDistance;this.weight=weight;}
    public String id(){return id;} public int minimumDistance(){return minimumDistance;} public double weight(){return weight;}
    public static Optional<StructureDefinition> byId(String id){return Arrays.stream(values()).filter(v->v.id.equalsIgnoreCase(id)).findFirst();}
    public static List<StructureDefinition> eligibleAt(double distance){
        if(!Double.isFinite(distance)||distance<0)return List.of();
        return Arrays.stream(values()).filter(v->distance>=v.minimumDistance).toList();
    }
    public static Optional<StructureDefinition> select(double distance,long seed){
        List<StructureDefinition> choices=eligibleAt(distance); if(choices.isEmpty())return Optional.empty();
        double total=choices.stream().mapToDouble(StructureDefinition::weight).sum();
        Random random=new Random(seed^0xBB67AE8584CAA73BL); double roll=random.nextDouble()*total;
        for(StructureDefinition choice:choices){roll-=choice.weight;if(roll<=0)return Optional.of(choice);}
        return Optional.of(choices.get(choices.size()-1));
    }
}
