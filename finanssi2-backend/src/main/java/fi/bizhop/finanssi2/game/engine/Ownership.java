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

    public List<PropertyState> propertiesOf(String uid) {
        return state.getProperties().stream().filter(property -> uid.equals(property.getOwner())).toList();
    }

    public List<Share> sharesOf(String uid) {
        return state.getShares().stream()
                .filter(share -> uid.equals(share.getOwner()))
                .map(share -> gameData.share(share.getId()))
                .toList();
    }

    /** Groups in which the player owns at least one property; Pysäköintitalo is in no group */
    public Set<String> groupsWithPropertiesOf(String uid) {
        return propertiesOf(uid).stream()
                .map(property -> gameData.titleDeed(property.getSquare()).group())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /** All properties and all shares of the group. Fund shares belong to no group, so never make one complete. */
    public boolean ownsCompleteGroup(String uid, String group) {
        if (group == null) {
            return false;
        }
        var properties = gameData.group(group).properties().stream().allMatch(square -> uid.equals(state.property(square).getOwner()));
        var shares = gameData.sharesOf(group).stream().allMatch(share -> uid.equals(state.share(share.id()).getOwner()));
        return properties && shares;
    }

    /** The player's industrial plants (squares 26, 27, 29, 30, 32, 33) */
    public long plantCount(String uid) {
        return builtPropertiesOf(uid).stream().filter(property -> gameData.titleDeed(property.getSquare()).building().industrial()).count();
    }

    /** The player's buildings other than industrial plants */
    public long otherBuildingCount(String uid) {
        return builtPropertiesOf(uid).size() - plantCount(uid);
    }

    List<PropertyState> builtPropertiesOf(String uid) {
        return propertiesOf(uid).stream().filter(PropertyState::isBuilt).toList();
    }

    /** Sum of the prices of the player's shares */
    public int shareCapital(String uid) {
        return sharesOf(uid).stream().mapToInt(Share::value).sum();
    }

    /** Share capital without the shares of the complete groups the player owns; fund shares count */
    public int shareCapitalOutsideCompleteGroups(String uid) {
        return sharesOf(uid).stream().filter(share -> !ownsCompleteGroup(uid, share.group())).mapToInt(Share::value).sum();
    }
}
