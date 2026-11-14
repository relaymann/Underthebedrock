package dev.underthebedrock.horror;

/** Ordered encounter intensity. Higher values are rarer and should need stronger context. */
public enum OccurrenceSeverity {
    NORMAL(0, 0.42),
    ODD(1, 0.28),
    STRANGE(2, 0.17),
    WEIRD(3, 0.085),
    SCARY(4, 0.035),
    TERRIFYING(5, 0.01);

    private final int rank;
    private final double baseWeight;

    OccurrenceSeverity(int rank, double baseWeight) {
        this.rank = rank;
        this.baseWeight = baseWeight;
    }

    public int rank() { return rank; }
    public double baseWeight() { return baseWeight; }
}
