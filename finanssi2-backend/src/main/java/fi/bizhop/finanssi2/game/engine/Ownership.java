package fi.bizhop.finanssi2.game.engine;

import fi.bizhop.finanssi2.game.data.GameData;
import fi.bizhop.finanssi2.game.data.Share;

import java.util.List;

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

    /** All properties and all shares of the group. Fund shares belong to no group, so never make one complete. */
    public boolean ownsCompleteGroup(String uid, String group) {
        if (group == null) {
            return false;
        }
        var properties = gameData.group(group).properties().stream().allMatch(square -> uid.equals(state.property(square).getOwner()));
        var shares = gameData.sharesOf(group).stream().allMatch(share -> uid.equals(state.share(share.id()).getOwner()));
        return properties && shares;
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
