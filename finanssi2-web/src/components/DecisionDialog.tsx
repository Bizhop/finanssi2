import { type ReactNode, type RefObject, useLayoutEffect, useState } from "react"
import { Box, Button, Chip, Dialog, DialogContent, DialogTitle, IconButton, InputAdornment, Stack, TextField, Tooltip, Typography } from "@mui/material"
import PaymentsOutlined from "@mui/icons-material/PaymentsOutlined"
import AccountBalanceOutlined from "@mui/icons-material/AccountBalanceOutlined"
import LocalFireDepartmentOutlined from "@mui/icons-material/LocalFireDepartmentOutlined"
import ArrowForward from "@mui/icons-material/ArrowForward"
import ArrowBack from "@mui/icons-material/ArrowBack"
import GavelOutlined from "@mui/icons-material/GavelOutlined"
import BlockOutlined from "@mui/icons-material/BlockOutlined"
import Check from "@mui/icons-material/Check"
import Add from "@mui/icons-material/Add"
import Remove from "@mui/icons-material/Remove"
import SwapHoriz from "@mui/icons-material/SwapHoriz"
import type { Game, GameBoardData } from "./gameApi.ts"
import { decisionLabels, euros, newsDestination, nextAuctionBid, propertySaleProceeds, shareSaleProceeds, validAuctionBid } from "./gameDecisions.ts"
import { GameBoard } from "./GameBoard.tsx"
import { DecisionAsset } from "./DecisionAsset.tsx"
import { DecisionSquare } from "./DecisionSquare.tsx"
import { PlayerToken } from "./PlayerToken.tsx"

type Pending = Game["state"]["pendingDecisions"][number]

const Choice = ({ label, children, icon, disabled, onClick }: {
    label: string
    children: ReactNode
    icon: ReactNode
    disabled?: boolean
    onClick: () => void
}) => (
    <Tooltip title={label}>
        <span>
            <Button
                size="small"
                variant="outlined"
                aria-label={label}
                disabled={disabled}
                onClick={onClick}
                startIcon={icon}
                sx={{ minHeight: 40, textTransform: "none", lineHeight: 1.25, textAlign: "left" }}
            >
                {children}
            </Button>
        </span>
    </Tooltip>
)

/** A modal anchored to the board's open area. Its portal and backdrop leave the game layout unchanged. */
export const DecisionDialog = ({ pending, allowed, send, game, board, busy, anchor, sourceStockTip }: {
    pending: Pending
    allowed: string[]
    send: (command: Record<string, unknown>) => Promise<void>
    game: Game
    board: GameBoardData | null
    busy: boolean
    anchor: RefObject<HTMLDivElement | null>
    sourceStockTip?: string | null
}) => {
    const [position, setPosition] = useState({ top: 80, left: globalThis.innerWidth / 2 })
    const [bid, setBid] = useState("0")
    const [destination, setDestination] = useState<number | null>(null)
    const [burned, setBurned] = useState<number[]>([])
    useLayoutEffect(() => {
        const update = () => {
            const rect = anchor.current?.getBoundingClientRect()
            if (!rect) return
            const width = Math.min(pending.card === "PV-17" ? 580 : 500, innerWidth - 24)
            const top = Math.max(12, Math.min(rect.top + rect.height * 0.22, innerHeight * 0.28))
            setPosition({ top, left: Math.max(width / 2 + 12, Math.min(rect.left + rect.width / 2, innerWidth - width / 2 - 12)) })
        }
        update()
        const observer = new ResizeObserver(update)
        if (anchor.current) observer.observe(anchor.current)
        globalThis.addEventListener("resize", update)
        globalThis.addEventListener("scroll", update, true)
        return () => {
            observer.disconnect()
            globalThis.removeEventListener("resize", update)
            globalThis.removeEventListener("scroll", update, true)
        }
    }, [anchor, pending.card])

    const labels = decisionLabels(game, board)
    const actor = game.state.players.find((player) => player.uid === pending.player)
    const cash = actor?.cash ?? 0
    const charges = Array.isArray(pending.charges) ? pending.charges as { reason: string }[] : []
    const source = pending.card ??
        (pending.type === "AssetAuction"
            ? "PV-36"
            : pending.type === "RaiseFunds"
            ? charges.some((charge) => charge.reason === "FINANCE_NEWS") ? game.state.activeFinanceNews : sourceStockTip
            : null)
    const card = [...board?.stockTips ?? [], ...board?.financeNews ?? []].find((card) => card.id === source)
    const heading = card?.chapters.find((chapter) => chapter.type === "header")?.text ?? ({
        RaiseFunds: "Settle payment",
        BondOffer: "Choose a bond",
        BondAuction: "Bond auction",
        AssetAuction: "Asset auction",
    }[pending.type] ?? "Your decision")
    const minimum = pending.type === "AssetAuction" ? Number(pending.minimumBid) : 500
    const options = Array.isArray(pending.options) ? pending.options.map(String) : []
    const isAuction = ["BondAuction", "AssetAuction"].includes(pending.type)
    const singleFire = pending.card === "PV-10" && options.length === 1
    const fireSquares = [...new Set(options.flatMap((option) => option.split(",").map(Number)))]
    const fireOption = singleFire ? options[0] : options.find((option) => {
        const squares = option.split(",").map(Number)
        return squares.length === burned.length && squares.every((square) => burned.includes(square))
    })
    const choose = (option: string) => void send({ type: "ChooseStockTipOption", option })
    const action = (label: string, text: ReactNode, icon: ReactNode, command: Record<string, unknown>, disabled = false) => (
        <Choice
            label={label}
            icon={icon}
            disabled={busy || disabled}
            onClick={() => void send(command)}
        >
            {text}
        </Choice>
    )
    const asset = (id: string, label?: string, onSelect?: () => void, children?: ReactNode, disabled = false, selected = false) => (
        <DecisionAsset asset={id} game={game} board={board} label={label} onSelect={onSelect} disabled={busy || disabled} selected={selected}>
            {children}
        </DecisionAsset>
    )
    const person = (uid: string) => {
        const player = game.state.players.find((player) => player.uid === uid)
        return player && (
            <Tooltip title={player.name}>
                <Stack direction="row" spacing={0.75} sx={{ alignItems: "center", color: "text.primary" }}>
                    <PlayerToken player={player} size="24px" />
                    <Typography variant="caption">{player.name}</Typography>
                </Stack>
            </Tooltip>
        )
    }
    const summary = pending.type === "RaiseFunds"
        ? `${euros(Number(pending.amount))} to ${pending.creditor ? labels.player(String(pending.creditor)) : "the bank"}`
        : pending.type === "BondOffer"
        ? "€500 per bond"
        : pending.type === "AssetAuction"
        ? `Minimum bid ${euros(minimum)} · sealed bids`
        : pending.type === "BondAuction"
        ? "Sealed bids · the winner receives a random available bond"
        : pending.card === "PV-10"
        ? singleFire ? "These buildings must be returned. Continue to roll for insurance." : "Choose two buildings to return."
        : pending.card === "PV-17"
        ? "Select a highlighted property on the board."
        : null
    const sellLabel = (amount: number) => `Sell to bank for ${amount.toLocaleString("en-US")}€`
    const stepBid = (direction: 1 | -1) => {
        const next = nextAuctionBid(bid, direction, minimum)
        if (next <= cash) setBid(String(next))
    }
    return (
        <Dialog
            open
            aria-labelledby="decision-title"
            aria-describedby="decision-description"
            maxWidth={false}
            slotProps={{
                backdrop: { sx: { bgcolor: "rgba(70, 76, 82, 0.32)" } },
                paper: {
                    sx: {
                        position: "fixed",
                        top: position.top,
                        left: position.left,
                        transform: "translateX(-50%)",
                        m: 0,
                        width: "calc(100vw - 24px)",
                        maxWidth: pending.card === "PV-17" ? 580 : 500,
                        maxHeight: `calc(100dvh - ${position.top + 12}px)`,
                        borderRadius: 2,
                    },
                },
            }}
        >
            <DialogTitle id="decision-title" sx={{ px: 2, pt: 1.5, pb: 0.75 }}>
                <Typography variant="overline" color="text.secondary" sx={{ lineHeight: 1.4 }}>Your decision</Typography>
                <Typography component="span" sx={{ display: "block", fontSize: 18, fontWeight: 600, lineHeight: 1.3 }}>{heading}</Typography>
            </DialogTitle>
            <DialogContent sx={{ px: 2, pb: 2 }}>
                <Stack spacing={1.25}>
                    <Box id="decision-description">
                        {card?.chapters.filter((_, index) => index !== card.chapters.findIndex((chapter) => chapter.type === "header")).map((
                            chapter,
                            index,
                        ) => (
                            <Typography
                                key={index}
                                variant="body2"
                                sx={{ mb: 0.5, fontStyle: chapter["font-style"], fontWeight: chapter.type === "header" ? 600 : undefined }}
                            >
                                {chapter.text}
                            </Typography>
                        ))}
                        {summary && <Typography variant="body2" color="text.secondary">{summary}</Typography>}
                    </Box>
                    <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap" }}>
                        <Tooltip title="Available cash">
                            <Chip size="small" icon={<PaymentsOutlined />} label={euros(cash)} />
                        </Tooltip>
                        {pending.type === "RaiseFunds" && (
                            <Tooltip title="Still needed to settle this payment">
                                <Chip size="small" color="warning" label={`−${euros(Math.max(0, Number(pending.amount) - cash))}`} />
                            </Tooltip>
                        )}
                        {isAuction && (
                            <Tooltip title="Minimum nonzero bid">
                                <Chip size="small" icon={<GavelOutlined />} label={euros(minimum)} />
                            </Tooltip>
                        )}
                    </Stack>
                    <Box component="fieldset" disabled={busy} sx={{ border: 0, p: 0, m: 0, minWidth: 0, "&:disabled": { opacity: 0.6 } }}>
                        <Stack spacing={1.25}>
                            {pending.type === "StockTipChoice" && pending.card === "PV-17" && (
                                <>
                                    <GameBoard
                                        game={game}
                                        board={board}
                                        turnStockTip={null}
                                        selectable={busy ? [] : options.map(Number)}
                                        onSelectSquare={setDestination}
                                        selectedSquare={destination}
                                    />
                                    {destination !== null && (
                                        <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
                                            {asset(`P:${destination}`)}
                                            {action(
                                                `Move to ${labels.property(String(destination))}`,
                                                `Move to ${labels.property(String(destination))}`,
                                                <ArrowForward />,
                                                { type: "ChooseStockTipOption", option: String(destination) },
                                                !allowed.includes("ChooseStockTipOption"),
                                            )}
                                        </Stack>
                                    )}
                                </>
                            )}
                            {pending.type === "StockTipChoice" && pending.card === "PV-10" && (
                                <>
                                    <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap" }}>
                                        {fireSquares.map((square) => (
                                            <Box key={square}>
                                                {asset(
                                                    `P:${square}`,
                                                    `Select ${labels.property(String(square))} to burn`,
                                                    singleFire
                                                        ? undefined
                                                        : () =>
                                                            setBurned((current) =>
                                                                current.includes(square) ? current.filter((value) => value !== square) : [...current, square]
                                                            ),
                                                    undefined,
                                                    !singleFire && !burned.includes(square) && burned.length >= 2,
                                                    singleFire || burned.includes(square),
                                                )}
                                            </Box>
                                        ))}
                                    </Stack>
                                    {action(
                                        singleFire ? "Confirm fire and roll for insurance" : "Burn selected buildings and roll for insurance",
                                        singleFire ? "Continue" : `Burn buildings (${burned.length}/2)`,
                                        singleFire ? <Check /> : <LocalFireDepartmentOutlined />,
                                        { type: "ChooseStockTipOption", option: fireOption },
                                        !fireOption || !allowed.includes("ChooseStockTipOption"),
                                    )}
                                </>
                            )}
                            {pending.type === "StockTipChoice" && !["PV-10", "PV-17"].includes(String(pending.card)) &&
                                (
                                    <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap", alignItems: "flex-start" }}>
                                        {options.map((option) => {
                                            const label = labels.option(String(pending.card), option)
                                            const disabled = !allowed.includes("ChooseStockTipOption")
                                            if (pending.card === "PV-24") {
                                                const parts = option.split(":")
                                                return (
                                                    <Stack key={option} spacing={0.75} sx={{ alignItems: "center" }}>
                                                        {parts[0] === "P" && (
                                                            <Stack direction="row" spacing={0.5} sx={{ alignItems: "center" }}>
                                                                {person(parts[1])}
                                                                <ArrowForward fontSize="small" />
                                                            </Stack>
                                                        )}
                                                        {asset(`B:${parts.at(-1)}`, label, () => choose(option), undefined, disabled)}
                                                    </Stack>
                                                )
                                            }
                                            if (["PV-31", "PV-35", "PV-36"].includes(String(pending.card))) {
                                                const id = pending.card === "PV-31" ? `P:${option}` : option
                                                return (
                                                    <Box key={option}>
                                                        {asset(
                                                            id,
                                                            label,
                                                            () => choose(option),
                                                            pending.card === "PV-31"
                                                                ? <Typography variant="caption">{label.match(/€[\d,]+/)?.[0]}</Typography>
                                                                : undefined,
                                                            disabled,
                                                        )}
                                                    </Box>
                                                )
                                            }
                                            if (pending.card === "PV-38" && option !== "PASS") {
                                                const [mine, other, theirs] = option.split("|")
                                                return (
                                                    <Stack key={option} spacing={0.75} sx={{ alignItems: "center" }}>
                                                        {person(other)}
                                                        <Stack direction="row" spacing={0.5} sx={{ alignItems: "center" }}>
                                                            {asset(`S:${mine}`)}
                                                            <SwapHoriz fontSize="small" />
                                                            {asset(`S:${theirs}`)}
                                                        </Stack>
                                                        <Choice label={label} icon={<SwapHoriz />} disabled={busy || disabled} onClick={() => choose(option)}>
                                                            Swap
                                                        </Choice>
                                                    </Stack>
                                                )
                                            }
                                            return (
                                                <Choice
                                                    key={option}
                                                    label={label}
                                                    icon={option === "PAY"
                                                        ? <ArrowForward />
                                                        : option === "BAIL"
                                                        ? <PaymentsOutlined />
                                                        : option === "JAIL"
                                                        ? <BlockOutlined />
                                                        : <Check />}
                                                    disabled={busy || disabled || (pending.card === "PV-26" && option === "PAY" && cash < 25000)}
                                                    onClick={() => choose(option)}
                                                >
                                                    {option === "PAY"
                                                        ? "€25,000"
                                                        : option === "BAIL"
                                                        ? "€30,000 bail"
                                                        : option === "JAIL"
                                                        ? "Jail"
                                                        : pending.card === "PV-38"
                                                        ? "Keep shares"
                                                        : "Stay"}
                                                </Choice>
                                            )
                                        })}
                                    </Stack>
                                )}
                            {pending.type === "BondOffer" && (
                                <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap", alignItems: "center" }}>
                                    {allowed.includes("BuyBond") && game.state.bonds.filter((bond) => !bond.owner).map((bond) => (
                                        <Box key={bond.number}>
                                            {asset(
                                                `B:${bond.number}`,
                                                `Buy bond ${bond.number} · €500`,
                                                () => void send({ type: "BuyBond", number: bond.number }),
                                            )}
                                        </Box>
                                    ))}
                                    {action("Pass on buying a bond", "Pass", <BlockOutlined />, { type: "Pass" }, !allowed.includes("Pass"))}
                                </Stack>
                            )}
                            {pending.type === "NewsDirection" && (
                                <Stack direction="row" spacing={1}>
                                    {[false, true].map((forward) => {
                                        const square = newsDestination(actor?.position ?? 1, forward, board)
                                        const label = `Move ${forward ? "forward" : "backward"} to ${
                                            board?.squares.find((item) => item.square === square)?.name ?? square
                                        } (${square})`
                                        return (
                                            <Button
                                                key={String(forward)}
                                                variant="outlined"
                                                aria-label={label}
                                                disabled={busy || !allowed.includes("ChooseNewsDirection")}
                                                onClick={() => void send({ type: "ChooseNewsDirection", forward })}
                                                sx={{ flex: 1, p: 1, minWidth: 0, textTransform: "none" }}
                                            >
                                                <Stack spacing={1} sx={{ alignItems: "center" }}>
                                                    {forward ? <ArrowForward /> : <ArrowBack />}
                                                    <DecisionSquare square={square} board={board} />
                                                </Stack>
                                            </Button>
                                        )
                                    })}
                                </Stack>
                            )}
                            {isAuction && (
                                <>
                                    <Stack direction="row" spacing={1.5} sx={{ alignItems: "center" }}>
                                        {asset(pending.type === "AssetAuction" ? String(pending.asset) : "B:?")}
                                        {pending.seller != null && person(String(pending.seller))}
                                    </Stack>
                                    <Stack direction="row" spacing={1} sx={{ alignItems: "flex-start" }}>
                                        <TextField
                                            size="small"
                                            label="Your bid (€)"
                                            type="number"
                                            value={bid}
                                            onChange={(event) => setBid(event.target.value)}
                                            onKeyDown={(event) => {
                                                if (event.key === "ArrowUp" || event.key === "ArrowDown") {
                                                    event.preventDefault()
                                                    stepBid(event.key === "ArrowUp" ? 1 : -1)
                                                }
                                            }}
                                            error={bid !== "" && !validAuctionBid(bid, cash, minimum)}
                                            helperText="€500 increments · 0 to pass"
                                            slotProps={{
                                                htmlInput: { min: 0, max: cash, step: 500 },
                                                input: {
                                                    endAdornment: (
                                                        <InputAdornment position="end">
                                                            <IconButton
                                                                size="small"
                                                                aria-label="Lower bid"
                                                                disabled={busy || Number(bid) <= 0}
                                                                onClick={() => stepBid(-1)}
                                                            >
                                                                <Remove fontSize="small" />
                                                            </IconButton>
                                                            <IconButton
                                                                size="small"
                                                                aria-label="Raise bid"
                                                                disabled={busy || nextAuctionBid(bid, 1, minimum) > cash}
                                                                onClick={() => stepBid(1)}
                                                            >
                                                                <Add fontSize="small" />
                                                            </IconButton>
                                                        </InputAdornment>
                                                    ),
                                                },
                                            }}
                                            sx={{
                                                flex: 1,
                                                minWidth: 0,
                                                "& input::-webkit-inner-spin-button, & input::-webkit-outer-spin-button": { WebkitAppearance: "none", m: 0 },
                                                "& input": { MozAppearance: "textfield" },
                                            }}
                                        />
                                        {action("Submit sealed bid", Number(bid) === 0 && bid !== "" ? "Pass" : "Bid", <GavelOutlined />, {
                                            type: pending.type === "BondAuction" ? "BidBond" : "BidAsset",
                                            amount: Number(bid),
                                        }, !allowed.includes(pending.type === "BondAuction" ? "BidBond" : "BidAsset") || !validAuctionBid(bid, cash, minimum))}
                                    </Stack>
                                </>
                            )}
                            {pending.type === "RaiseFunds" && (
                                <>
                                    <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap", alignItems: "flex-start" }}>
                                        {allowed.includes("TakeLoan") && asset(
                                            "LOAN",
                                            undefined,
                                            undefined,
                                            <Tooltip title="Take a bank loan for 50,000€">
                                                <IconButton
                                                    size="small"
                                                    aria-label="Take a €50,000 bank loan"
                                                    disabled={busy}
                                                    onClick={() => void send({ type: "TakeLoan" })}
                                                >
                                                    <AccountBalanceOutlined fontSize="small" />
                                                </IconButton>
                                            </Tooltip>,
                                        )}
                                        {allowed.includes("SellCar") && asset(
                                            "CAR",
                                            undefined,
                                            undefined,
                                            <Tooltip title={sellLabel(25000)}>
                                                <IconButton size="small" aria-label="Sell car" disabled={busy} onClick={() => void send({ type: "SellCar" })}>
                                                    <PaymentsOutlined fontSize="small" />
                                                </IconButton>
                                            </Tooltip>,
                                        )}
                                        {game.state.properties.filter((property) => {
                                            const deed = board?.titleDeeds.find((deed) => deed.square === property.square)
                                            const state = property.built ? "built" : "unbuilt"
                                            return property.owner === pending.player && !property.mortgaged &&
                                                ((allowed.includes("Mortgage") && deed?.mortgage?.[state] != null) ||
                                                    (allowed.includes("SellBackProperty") && deed?.buyBack?.[state] != null))
                                        }).map((property) => {
                                            const deed = board!.titleDeeds.find((deed) => deed.square === property.square)!
                                            const state = property.built ? "built" : "unbuilt"
                                            return (
                                                <Box key={property.square}>
                                                    {asset(
                                                        `P:${property.square}`,
                                                        undefined,
                                                        undefined,
                                                        <Stack direction="row">
                                                            {allowed.includes("Mortgage") && deed.mortgage?.[state] != null && (
                                                                <Tooltip title={`Mortgage for ${euros(deed.mortgage[state]!)}`}>
                                                                    <IconButton
                                                                        size="small"
                                                                        aria-label={`Mortgage ${deed.name}`}
                                                                        disabled={busy}
                                                                        onClick={() =>
                                                                            void send({ type: "Mortgage", square: property.square })}
                                                                    >
                                                                        <AccountBalanceOutlined fontSize="small" />
                                                                    </IconButton>
                                                                </Tooltip>
                                                            )}
                                                            {allowed.includes("SellBackProperty") && deed.buyBack?.[state] != null && (
                                                                <Tooltip
                                                                    title={sellLabel(
                                                                        propertySaleProceeds(
                                                                            deed.buyBack[state]!,
                                                                            property.built,
                                                                            game.state.activeFinanceNews,
                                                                        ),
                                                                    )}
                                                                >
                                                                    <IconButton
                                                                        size="small"
                                                                        aria-label={`Sell ${deed.name}`}
                                                                        disabled={busy}
                                                                        onClick={() => void send({ type: "SellBackProperty", square: property.square })}
                                                                    >
                                                                        <PaymentsOutlined fontSize="small" />
                                                                    </IconButton>
                                                                </Tooltip>
                                                            )}
                                                        </Stack>,
                                                    )}
                                                </Box>
                                            )
                                        })}
                                        {allowed.includes("SellBackShare") &&
                                            game.state.shares.filter((share) => share.owner === pending.player).map((share) => (
                                                <Box key={share.id}>
                                                    {asset(
                                                        `S:${share.id}`,
                                                        undefined,
                                                        undefined,
                                                        <Tooltip
                                                            title={board?.shares.find((item) => item.id === share.id)
                                                                ? sellLabel(
                                                                    shareSaleProceeds(
                                                                        board.shares.find((item) => item.id === share.id)!.buyBack,
                                                                        game.state.activeFinanceNews,
                                                                    ),
                                                                )
                                                                : "Sell to bank"}
                                                        >
                                                            <IconButton
                                                                size="small"
                                                                aria-label={`Sell ${labels.share(share.id)}`}
                                                                disabled={busy}
                                                                onClick={() => void send({ type: "SellBackShare", share: share.id })}
                                                            >
                                                                <PaymentsOutlined fontSize="small" />
                                                            </IconButton>
                                                        </Tooltip>,
                                                    )}
                                                </Box>
                                            ))}
                                    </Stack>
                                    <Stack direction="row" spacing={1}>
                                        {allowed.includes("Pay") && action("Settle payment", "Pay", <Check />, { type: "Pay" })}
                                        {allowed.includes("DeclareBankruptcy") &&
                                            action("Declare bankruptcy", "Bankruptcy", <BlockOutlined />, { type: "DeclareBankruptcy" })}
                                    </Stack>
                                </>
                            )}
                        </Stack>
                    </Box>
                </Stack>
            </DialogContent>
        </Dialog>
    )
}
