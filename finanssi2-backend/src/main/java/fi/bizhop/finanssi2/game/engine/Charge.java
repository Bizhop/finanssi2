package fi.bizhop.finanssi2.game.engine;

/** One part of a payment a player must make */
public record Charge(int amount, MoneyReason reason) {}
