package fi.bizhop.finanssi2.game.engine;

import fi.bizhop.finanssi2.game.data.GameData;
import fi.bizhop.finanssi2.game.data.Share;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Who owns what, and what follows from it: complete business groups and share capital */
public class Ownership {
    final GameData gameData;
    final GameState state;

    public Ownership(GameData gameData, GameState state) {
        this.gameData = gameData;
        this.state = state;
    }

    public List<PropertyState> propertiesOf(String playerId) {
        return state.getProperties().stream().filter(property -> playerId.equals(property.getOwner())).toList();
    }

    public List<Share> sharesOf(String playerId) {
        return state.getShares().stream()
                .filter(share -> playerId.equals(share.getOwner()))
                .map(share -> gameData.share(share.getId()))
                .toList();
    }

    /** Groups in which the player owns at least one property; Pysäköintitalo is in no group */
    public Set<String> groupsWithPropertiesOf(String playerId) {
        return propertiesOf(playerId).stream()
                .map(property -> gameData.titleDeed(property.getSquare()).group())
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** All properties and all shares of the group. Fund shares belong to no group, so never make one complete. */
    public boolean ownsCompleteGroup(String playerId, String group) {
        if (group == null) {
            return false;
        }
        var properties = gameData.group(group).properties().stream().allMatch(square -> playerId.equals(state.property(square).getOwner()));
        var shares = gameData.sharesOf(group).stream().allMatch(share -> playerId.equals(state.share(share.id()).getOwner()));
        return properties && shares;
    }

    /** The player's industrial plants (squares 26, 27, 29, 30, 32, 33) */
    public long plantCount(String playerId) {
        return builtPropertiesOf(playerId).stream().filter(property -> gameData.titleDeed(property.getSquare()).building().industrial()).count();
    }

    /** The player's buildings other than industrial plants */
    public long otherBuildingCount(String playerId) {
        return builtPropertiesOf(playerId).size() - plantCount(playerId);
    }

    List<PropertyState> builtPropertiesOf(String playerId) {
        return propertiesOf(playerId).stream().filter(PropertyState::isBuilt).toList();
    }

    /** Sum of the prices of the player's shares */
    public int shareCapital(String playerId) {
        return sharesOf(playerId).stream().mapToInt(Share::value).sum();
    }

    /** Share capital without the shares of the complete groups the player owns; fund shares count */
    public int shareCapitalOutsideCompleteGroups(String playerId) {
        return sharesOf(playerId).stream().filter(share -> !ownsCompleteGroup(playerId, share.group())).mapToInt(Share::value).sum();
    }
}
