package fi.bizhop.finanssi2.game.data;

/** A title deed value for an unbuilt and a built property; null where the card shows a dash (not available in that state) */
public record ByState(Integer unbuilt, Integer built) {
    public Integer get(boolean isBuilt) {
        return isBuilt ? built : unbuilt;
    }
}
