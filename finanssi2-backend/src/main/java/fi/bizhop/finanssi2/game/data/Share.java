package fi.bizhop.finanssi2.game.data;

/**
 * A share certificate. {@code value} is its price; {@code dividend} (value × {@code dividendPercent}) and {@code buyBack} are printed
 * on it. {@code group} is null for the two fund shares ("Rahasto-osake").
 */
public record Share(String id, String group, int value, int dividendPercent, int dividend, int buyBack) {
    public boolean fund() {
        return group == null;
    }
}
