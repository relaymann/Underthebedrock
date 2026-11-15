package dev.underthebedrock.base;

public final class BaseUpgradeRules {
    private BaseUpgradeRules() {}
    public static int clampLevel(int level){return Math.max(0,Math.min(3,level));}
    public static double forgeMultiplier(double baseMultiplier,int level){return baseMultiplier*Math.pow(0.85,clampLevel(level));}
    public static long farmIntervalMillis(int level){return Math.max(6L*60_000L,15L*60_000L-clampLevel(level)*3L*60_000L);}
    public static int foodPrinterOutput(int level){return 2+clampLevel(level)*2;}
    public static long foodPrinterCooldownMillis(int level){return Math.max(45_000L,120_000L-clampLevel(level)*25_000L);}
    public static double securityRadius(int tier,int level){return 10.0+Math.max(1,Math.min(4,tier))*7.5+clampLevel(level)*8.0;}
    public static long securityAlertCooldownMillis(int level){return Math.max(12_000L,30_000L-clampLevel(level)*6_000L);}
}