package fi.bizhop.finanssi2.game.engine;

/** A command the rules do not allow in the current state; the state is left unchanged */
public class RuleViolation extends RuntimeException {
    public RuleViolation(String reason) {
        super(reason);
    }
}
