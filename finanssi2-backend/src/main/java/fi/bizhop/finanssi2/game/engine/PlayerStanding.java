package fi.bizhop.finanssi2.game.engine;

import java.util.List;

public record PlayerStanding(String player, int cash, int netWorth, List<String> completeGroups) {
    public PlayerStanding {
        completeGroups = List.copyOf(completeGroups);
    }
}
