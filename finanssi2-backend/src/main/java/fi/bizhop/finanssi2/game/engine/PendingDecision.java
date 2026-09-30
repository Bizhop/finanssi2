package fi.bizhop.finanssi2.game.engine;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/**
 * Input the game waits for before play continues, possibly from a player whose turn it is not. Only the addressed player of the
 * first decision in the queue may act, and only with the commands that decision allows.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, property = "type")
public sealed interface PendingDecision {
    String player();

    /**
     * The player owes more than their cash. They may raise funds, then pay, or go bankrupt if they cannot. {@code creditor} is a
     * player uid, or null for the bank.
     */
    record RaiseFunds(String player, String creditor, List<Charge> charges) implements PendingDecision {
        public RaiseFunds {
            charges = List.copyOf(charges);
        }

        @JsonProperty("amount")
        public int amount() {
            return charges.stream().mapToInt(Charge::amount).sum();
        }
    }
}
