package fi.bizhop.finanssi2.game.data;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A Finance News or Stock Tip card. Only the text is data; the engine maps card ids to effects, so ids must stay stable when
 * transcriptions replace mock text.
 */
public record Card(String id, @JsonProperty("type") Deck deck, List<CardChapter> chapters, boolean mock) {
    public Card {
        chapters = List.copyOf(chapters);
    }
}
