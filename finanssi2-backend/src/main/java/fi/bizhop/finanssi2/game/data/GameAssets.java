package fi.bizhop.finanssi2.game.data;

import java.util.List;
import java.util.Map;

/** Everything read from the data files, before validation */
public record GameAssets(
        List<Square> squares,
        List<BusinessGroup> groups,
        List<TitleDeed> titleDeeds,
        List<Share> shares,
        // Total share capital of each group, printed on its shares
        Map<String, Integer> groupShareCapital,
        List<Card> financeNews,
        List<Card> stockTips) {
    public GameAssets {
        squares = List.copyOf(squares);
        groups = List.copyOf(groups);
        titleDeeds = List.copyOf(titleDeeds);
        shares = List.copyOf(shares);
        groupShareCapital = Map.copyOf(groupShareCapital);
        financeNews = List.copyOf(financeNews);
        stockTips = List.copyOf(stockTips);
    }
}
