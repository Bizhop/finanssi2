package fi.bizhop.finanssi2.game.data;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum Deck {
    @JsonProperty("FINANSSILEHTI") FINANCE_NEWS,
    @JsonProperty("PORSSIVIHJE") STOCK_TIP
}
