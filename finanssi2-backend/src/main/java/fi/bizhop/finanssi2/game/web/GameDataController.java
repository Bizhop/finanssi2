package fi.bizhop.finanssi2.game.web;

import fi.bizhop.finanssi2.game.data.Deck;
import fi.bizhop.finanssi2.game.data.GameData;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GameDataController {
    final GameData gameData;

    @RequestMapping(value = "/api/game-data", method = RequestMethod.GET, produces = "application/json")
    GameBoardData get() {
        return new GameBoardData(gameData.squares(), gameData.groups(), gameData.titleDeeds(), gameData.shares(),
                gameData.cards(Deck.FINANCE_NEWS), gameData.cards(Deck.STOCK_TIP));
    }
}
