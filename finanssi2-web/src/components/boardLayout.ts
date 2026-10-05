/** Width / height of the rectified board image (assets/board.webp, 2155×1630 px) */
export const BOARD_ASPECT_RATIO = 2155 / 1630

/**
 * Square divider positions measured from the board image, in percent of the image width (top, bottom) or height (left, right).
 * Each side has its own lines because the printed board is not perfectly uniform.
 */
const DIVIDERS = {
    top: [16.35, 23.03, 29.72, 36.4, 43.1, 49.74, 56.39, 63.15, 69.84, 76.5, 83.22],
    bottom: [16.68, 23.48, 30.19, 36.84, 43.63, 50.37, 57.05, 63.9, 70.6, 77.21, 83.8],
    left: [21.17, 29.31, 37.5, 45.81, 53.9, 62.12, 70.37, 78.57],
    right: [21.4, 29.56, 37.74, 45.92, 54.17, 62.31, 70.58, 78.74],
}

/** Inner edges of the square ring, in percent */
const INNER = { top: 12.85, bottom: 87.15, left: 9.88, right: 90.04 }

export type SquareRect = { left: number; top: number; width: number; height: number }

const rect = (x0: number, y0: number, x1: number, y1: number): SquareRect => ({ left: x0, top: y0, width: x1 - x0, height: y1 - y0 })

/**
 * Position of a square on the board image, in percent. Squares run clockwise from 1 (bottom left corner):
 * 2–10 up the left side, 11 top left, 12–23 along the top, 24 top right, 25–33 down the right side, 34 bottom right and 35–46 along the bottom.
 */
export const squareRect = (square: number): SquareRect => {
    const { top, bottom, left, right } = INNER
    const xTop = [left, ...DIVIDERS.top, right]
    const xBottom = [left, ...DIVIDERS.bottom, right]
    const yLeft = [top, ...DIVIDERS.left, bottom]
    const yRight = [top, ...DIVIDERS.right, bottom]
    if (square === 1) return rect(0, bottom, left, 100)
    if (square <= 10) return rect(0, yLeft[10 - square], left, yLeft[11 - square])
    if (square === 11) return rect(0, 0, left, top)
    if (square <= 23) return rect(xTop[square - 12], 0, xTop[square - 11], top)
    if (square === 24) return rect(right, 0, 100, top)
    if (square <= 33) return rect(right, yRight[square - 25], 100, yRight[square - 24])
    if (square === 34) return rect(right, bottom, 100, 100)
    return rect(xBottom[46 - square], bottom, xBottom[47 - square], 100)
}

/** The two marked card places in the middle of the board, and the face-up card shown beside each deck, in percent */
const CARD = { width: 10.55, height: 18.93, top: 46.04 }
const CARD_GAP = 1.5
export const CARD_PLACES = {
    financeNews: {
        deck: { left: 30.07, top: CARD.top, width: CARD.width, height: CARD.height },
        drawn: { left: 30.07 - CARD_GAP - CARD.width, top: CARD.top, width: CARD.width, height: CARD.height },
    },
    stockTip: {
        deck: { left: 59.3, top: CARD.top, width: CARD.width, height: CARD.height },
        drawn: { left: 59.3 + CARD.width + CARD_GAP, top: CARD.top, width: CARD.width, height: CARD.height },
    },
}

/** Which side of the board a square is on; corners are 1, 11, 24 and 34 */
export const squareSide = (square: number): "left" | "top" | "right" | "bottom" | "corner" =>
    [1, 11, 24, 34].includes(square) ? "corner" : square <= 10 ? "left" : square <= 23 ? "top" : square <= 33 ? "right" : "bottom"
