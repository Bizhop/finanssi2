package fi.bizhop.finanssi2.game.data;

import com.fasterxml.jackson.annotation.JsonProperty;

/** A paragraph of card text. {@code type} is "header" for a heading, {@code fontStyle} "italic" for italic text, otherwise null. */
public record CardChapter(String text, String type, @JsonProperty("font-style") String fontStyle) {
    static final String HEADER = "header";
    static final String ITALIC = "italic";

    public boolean header() {
        return HEADER.equals(type);
    }

    public boolean italic() {
        return ITALIC.equals(fontStyle);
    }
}
