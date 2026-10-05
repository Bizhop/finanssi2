package fi.bizhop.finanssi2.game.engine;

/** When a shareholders' meeting can be called on a business group. */
public enum ShareholdersMeeting {
    /** Recommended (the default): every property and share of the group is bought from the bank, since a meeting takes over all of the group */
    ALL_ASSETS_BOUGHT,
    /** Original rules: another player owns some of the group; anything still in the bank stays out of the takeover */
    ANY_OTHER_OWNER
}
