package fi.bizhop.finanssi2.game.data;

/** What can be built on a property. {@code label} is the card's word in "<label> maksaa"; "Teollisuus" is an industrial plant. */
public record Building(String label, int price) {
    static final String INDUSTRIAL = "Teollisuus";

    public boolean industrial() {
        return INDUSTRIAL.equals(label);
    }
}
