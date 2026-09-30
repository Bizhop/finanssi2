package fi.bizhop.finanssi2.game.data;

import java.util.List;

/** A business group and its property squares */
public record BusinessGroup(String id, String name, String color, List<Integer> properties) {
    public BusinessGroup {
        properties = List.copyOf(properties);
    }
}
