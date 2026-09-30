package fi.bizhop.finanssi2.game.engine;

/** Source of die rolls: random in production, scripted in tests */
public interface Dice {
    /** One die, 1–6 */
    int roll();
}
