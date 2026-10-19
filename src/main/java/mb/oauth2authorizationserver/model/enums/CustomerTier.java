package mb.oauth2authorizationserver.model.enums;

import java.math.BigDecimal;

/**
 * Customer tiers with the price multiplier applied per amount band (Strategy-style lookup instead of if/else chains).
 */
public enum CustomerTier {
    GOLD("0.90", "0.80", "0.70"),
    SILVER("0.95", "0.85", "0.80"),
    BRONZE("0.98", "0.98", "0.98");

    public static final BigDecimal MEDIUM_THRESHOLD = BigDecimal.valueOf(1_000);
    public static final BigDecimal LARGE_THRESHOLD = BigDecimal.valueOf(5_000);

    private final BigDecimal baseMultiplier;
    private final BigDecimal mediumMultiplier;
    private final BigDecimal largeMultiplier;

    CustomerTier(String baseMultiplier, String mediumMultiplier, String largeMultiplier) {
        this.baseMultiplier = new BigDecimal(baseMultiplier);
        this.mediumMultiplier = new BigDecimal(mediumMultiplier);
        this.largeMultiplier = new BigDecimal(largeMultiplier);
    }

    public BigDecimal apply(BigDecimal amount) {
        if (amount.compareTo(LARGE_THRESHOLD) > 0) {
            return amount.multiply(largeMultiplier);
        }
        if (amount.compareTo(MEDIUM_THRESHOLD) > 0) {
            return amount.multiply(mediumMultiplier);
        }
        return amount.multiply(baseMultiplier);
    }
}
