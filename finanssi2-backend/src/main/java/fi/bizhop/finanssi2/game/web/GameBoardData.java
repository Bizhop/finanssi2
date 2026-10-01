package fi.bizhop.finanssi2.game.web;

import fi.bizhop.finanssi2.game.data.BusinessGroup;
import fi.bizhop.finanssi2.game.data.Card;
import fi.bizhop.finanssi2.game.data.Share;
import fi.bizhop.finanssi2.game.data.Square;
import fi.bizhop.finanssi2.game.data.TitleDeed;

import java.util.List;

public record GameBoardData(List<Square> squares, List<BusinessGroup> groups, List<TitleDeed> titleDeeds,
                            List<Share> shares, List<Card> financeNews, List<Card> stockTips) {
    public GameBoardData {
        squares = List.copyOf(squares);
        groups = List.copyOf(groups);
        titleDeeds = List.copyOf(titleDeeds);
        shares = List.copyOf(shares);
        financeNews = List.copyOf(financeNews);
        stockTips = List.copyOf(stockTips);
    }
}
