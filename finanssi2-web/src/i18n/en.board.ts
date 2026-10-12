import type { fiBoard } from "./fi.board.ts"

export const enBoard: Record<keyof typeof fiBoard, string> = {
    "board.loadingSquare": "Loading…",
    "board.financeNews": "Finance News",
    "board.stockTip": "Stock Tip",
    "board.financeNewsDeck": "Finance News deck",
    "board.stockTipDeck": "Stock Tip deck",
    "board.selectSquare": "Select {{name}}",
    "board.buySquare": "Buy {{name}}",
    "board.buildOn": "Build on {{name}}",
    "board.callMeeting": "Call a shareholders' meeting of {{group}}",

    "board.meeting.title": "Shareholders' meeting: {{group}}",
    "board.meeting.offer":
        "Show the purchase prices of the other players' properties (with buildings) and shares in the group, now {{takeover}}, and put down a brokerage fee of {{minFee}}–{{maxFee}}.",
    "board.meeting.roll":
        "Roll both dice. If the total is at most the fee divided by {{unit}}, you take over their properties and shares and pay them the purchase prices; {{bankShare}} of the fee goes to the bank and the rest is shared among them. Otherwise the takeover fails and the whole fee goes to the bank.",

    "board.card.industrialPlant": "Industrial plant",
    "board.card.building": "Building",

    "board.panel.you": "{{name}} (you)",
    "board.panel.out": "{{name}} · out",
    "board.panel.youOut": "{{name}} (you) · out",
    "board.panel.cash": "Cash",
    "board.panel.noLoans": "No bank loans",
    "board.panel.loans_one": "{{count}} bank loan",
    "board.panel.loans_other": "{{count}} bank loans",
    "board.panel.car": "Car",
    "board.panel.noCarBuy": "No car: click to buy one",
    "board.panel.noCar": "No car",
    "board.panel.properties": "Properties",
    "board.panel.sharesBuy": "Shares: click to buy one from the bank",
    "board.panel.shares": "Shares",
    "board.panel.fund": "fund",
    "board.panel.bonds": "Bonds",
    "board.panel.heldStockTips": "Held Stock Tips",
    "board.panel.rollTooltip": "Roll the dice",
    "board.panel.roll": "Roll",
    "board.panel.endTurn": "End turn",

    "board.window.activity": "Game activity",
}
