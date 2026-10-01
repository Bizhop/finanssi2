package fi.bizhop.finanssi2.game.engine;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import fi.bizhop.finanssi2.game.data.SquareType;

import java.util.List;

/** Something that happened in a game, in the order it happened. Serialized with its simple class name as {@code type}. */
@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, property = "type")
public sealed interface GameEvent {
    record PlayerJoined(String player, String name, int piece) implements GameEvent {}

    record PlayerLeft(String player) implements GameEvent {}

    /** A player's roll for the starting order; tied highest rollers roll again in the next round */
    record StartingRoll(String player, int round, List<Integer> dice) implements GameEvent {
        public StartingRoll {
            dice = List.copyOf(dice);
        }
    }

    record GameStarted(List<String> turnOrder, int startingCash) implements GameEvent {
        public GameStarted {
            turnOrder = List.copyOf(turnOrder);
        }
    }

    record TurnStarted(String player) implements GameEvent {}

    record DiceRolled(String player, List<Integer> dice) implements GameEvent {
        public DiceRolled {
            dice = List.copyOf(dice);
        }
    }

    record PieceMoved(String player, int from, int to) implements GameEvent {}

    record LandedOn(String player, int square) implements GameEvent {}

    record FinanceNewsDrawn(String player, String card, String replaced) implements GameEvent {}

    record StockTipDrawn(String player, String card, boolean held) implements GameEvent {}
    record StockTipUsed(String player, String card) implements GameEvent {}
    record CarLost(String player) implements GameEvent {}
    record BondTransferred(String from, String to, int number) implements GameEvent {}
    record BondGranted(String player, int number) implements GameEvent {}
    record AssetTransferred(String from, String to, String asset) implements GameEvent {}
    record SharesSwapped(String player, String given, String receivedFrom, String received) implements GameEvent {}
    record ShareIssued(String player, String share) implements GameEvent {}
    record ShareTaken(String player, String from, String share) implements GameEvent {}
    record ShareLost(String player, String share) implements GameEvent {}
    record AssetAuctionCompleted(String seller, String asset, int amount, String winner, List<PendingDecision.Bid> bids)
            implements GameEvent {
        public AssetAuctionCompleted { bids = List.copyOf(bids); }
    }
    record AssetAuctionStarted(String seller, String asset, int minimumBid, List<String> bidders) implements GameEvent {
        public AssetAuctionStarted { bidders = List.copyOf(bidders); }
    }
    record BuildingsBurned(String player, List<Integer> squares) implements GameEvent {
        public BuildingsBurned { squares = List.copyOf(squares); }
    }
    record StockTipDividendCharged(String player, String shareholder, List<String> shares, int amount) implements GameEvent {
        public StockTipDividendCharged { shares = List.copyOf(shares); }
    }
    record BailRoll(String player, List<Integer> dice, boolean bailReturned) implements GameEvent {
        public BailRoll { dice = List.copyOf(dice); }
    }
    record BondOneWon(String player, String previousOwner, int amount) implements GameEvent {}
    record ShareholdersMeetingResolved(String player, String group, int brokerageFee, int takeoverSum,
                                       List<Integer> dice, boolean success) implements GameEvent {
        public ShareholdersMeetingResolved { dice = List.copyOf(dice); }
    }

    record HeldStockTipsReturned(String player, List<String> cards) implements GameEvent {
        public HeldStockTipsReturned { cards = List.copyOf(cards); }
    }

    /** Landed on a square whose effect is not implemented yet */
    record NotImplemented(String player, int square, SquareType squareType) implements GameEvent {}

    record SettingsChanged(GameSettings settings) implements GameEvent {}

    record CarBought(String player) implements GameEvent {}

    record CarSold(String player) implements GameEvent {}

    /** {@code from} or {@code to} is null for the bank */
    record MoneyTransferred(String from, String to, int amount, MoneyReason reason) implements GameEvent {}

    /** {@code loans}: how many the player has now */
    record LoanTaken(String player, int loans) implements GameEvent {}

    record LoanRepaid(String player, int loans) implements GameEvent {}

    /** The player cannot pay from cash and must raise funds or go bankrupt; see {@link PendingDecision.RaiseFunds} */
    record PaymentDue(String player, String creditor, int amount) implements GameEvent {}

    /** Roll for the square 34 reward */
    record BankEntranceRoll(String player, List<Integer> dice) implements GameEvent {
        public BankEntranceRoll {
            dice = List.copyOf(dice);
        }
    }

    record PlayerBankrupt(String player, String creditor) implements GameEvent {}
    record PlayerResigned(String player) implements GameEvent {}
    record GameEnded(String winner, List<PlayerStanding> standings) implements GameEvent {
        public GameEnded { standings = List.copyOf(standings); }
    }

    /** The bankrupt player's properties (unmortgaged) and shares went back to the bank */
    record AssetsReturned(String player, List<Integer> properties, List<String> shares, List<Integer> bonds) implements GameEvent {
        public AssetsReturned {
            properties = List.copyOf(properties);
            shares = List.copyOf(shares);
            bonds = List.copyOf(bonds);
        }
    }

    record PropertyBought(String player, int square) implements GameEvent {}

    record ShareBought(String player, String share) implements GameEvent {}

    /** Rent for landing on another player's property, paid by {@link MoneyTransferred} or due by {@link PaymentDue} */
    record RentCharged(String player, String owner, int square, int amount, boolean doubled) implements GameEvent {}

    record PropertyMortgaged(String player, int square) implements GameEvent {}

    record PropertyRedeemed(String player, int square) implements GameEvent {}

    record PropertySoldBack(String player, int square) implements GameEvent {}

    record ShareSoldBack(String player, String share) implements GameEvent {}

    /** {@code industrial}: an industrial plant rather than another building */
    record PropertyBuilt(String player, int square, boolean industrial) implements GameEvent {}

    record TurnEnded(String player) implements GameEvent {}

    /** Roll on landing in jail (square 24): {@code missedTurns} turns are skipped */
    record JailRoll(String player, int die, int missedTurns) implements GameEvent {}

    /** Roll on square 36: on 1–2 the player goes to jail */
    record GoToJailRoll(String player, int die, boolean jailed) implements GameEvent {}

    /** Square 36 does not affect a player who has left jail and not been on square 1 since */
    record JailExempt(String player) implements GameEvent {}

    /** A turn the player missed in jail; {@code remaining}: turns still to skip */
    record TurnSkipped(String player, int remaining) implements GameEvent {}

    record BondBought(String player, int number) implements GameEvent {}
    record BondDrawn(int number, int prize, String winner) implements GameEvent {}
    record BondAuctionCompleted(int amount, String winner, int number, List<PendingDecision.Bid> bids) implements GameEvent {
        public BondAuctionCompleted {
            bids = List.copyOf(bids);
        }
    }

    /** The bank's dividend on the listed shares, paid by {@link MoneyTransferred} */
    record BankDividend(String player, int square, List<String> shares, int amount) implements GameEvent {
        public BankDividend {
            shares = List.copyOf(shares);
        }
    }

    /**
     * Dividend the player owes another player on square 41, for the listed shares, paid by {@link MoneyTransferred} or due by
     * {@link PaymentDue}
     */
    record PlayerDividendCharged(String player, String shareholder, int square, List<String> shares, int amount) implements GameEvent {
        public PlayerDividendCharged {
            shares = List.copyOf(shares);
        }
    }
}
