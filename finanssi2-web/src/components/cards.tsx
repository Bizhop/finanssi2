// Card-like renderings of the game's paper assets, modelled on the printed cards. Sizes are in em, so the parent's font size scales a card.
import type { ReactNode } from "react"
import { Box, Stack } from "@mui/material"
import HomeRounded from "@mui/icons-material/HomeRounded"
import FactoryRounded from "@mui/icons-material/FactoryRounded"

import type { GameBoardData } from "./gameApi.ts"

type TitleDeed = GameBoardData["titleDeeds"][number]
type Share = GameBoardData["shares"][number]

/** Printed colours of the group bands on title deeds */
const GROUP_COLORS: Record<string, { band: string; text: string }> = {
    KASITEOLLISUUS: { band: "#7cbf45", text: "#111" },
    PALVELUYHTIO: { band: "#7b31a6", text: "#fff" },
    LIIKEKESKUS: { band: "#22874a", text: "#111" },
    FINANSSIYHTYMA: { band: "#f0c419", text: "#111" },
    TEKNIIKKA: { band: "#7b5333", text: "#fff" },
    KEMIA: { band: "#dc4b2c", text: "#111" },
    TEOLLISUUSKONSERNI: { band: "#2a3aab", text: "#fff" },
}

/** Money as printed on the cards: "10 000" */
const amount = (value: number | null | undefined) => value == null ? "–" : value.toLocaleString("fi-FI")

const paper = {
    position: "relative",
    boxSizing: "border-box",
    flex: "none",
    overflow: "hidden",
    borderRadius: "0.4em",
    backgroundColor: "#f5f5f1",
    color: "#151515",
    fontFamily: "Helvetica, Arial, sans-serif",
    boxShadow: "0 0.2em 0.7em rgba(0,0,0,0.45)",
    lineHeight: 1.2,
} as const
const portrait = { ...paper, width: "13em", height: "18em" } as const
const landscape = { ...paper, width: "16em", height: "10.5em" } as const

const Row = ({ label, value }: { label: ReactNode; value?: ReactNode }) => (
    <Stack direction="row" sx={{ justifyContent: "space-between", gap: "0.5em" }}>
        <span>{label}</span>
        {value != null && <span style={{ whiteSpace: "nowrap" }}>{value}</span>}
    </Stack>
)

/** A title and its unbuilt/built values; `doubled` prints the values doubled in bold, for rent in a complete group */
const Section = ({ title, values, doubled = false }: { title: string; values: { unbuilt: number | null; built: number | null }; doubled?: boolean }) => {
    const value = (amountValue: number | null) => amount(doubled && amountValue != null ? amountValue * 2 : amountValue)
    return (
        <Box sx={{ mt: "0.45em", fontWeight: doubled ? 800 : undefined }}>
            <div>
                {title}
                {doubled && " ×2"}
            </div>
            <Row label="Rakentamaton tontti" value={value(values.unbuilt)} />
            <Row label="Rakennettu tontti" value={value(values.built)} />
        </Box>
    )
}

const Warning = ({ children }: { children: ReactNode }) => <Box sx={{ mt: "0.45em", color: "#c0262d", fontWeight: 700 }}>{children}</Box>

/** A title deed ("hallintatodistus"); a building shows as a house or factory in the corner and a mortgage as a stamp */
export const TitleDeedCard = ({ deed, groupName, built = false, mortgaged = false, rentDoubled = false }: {
    deed: TitleDeed
    groupName?: string
    built?: boolean
    mortgaged?: boolean
    /** The owner has the whole group, so rent is doubled */
    rentDoubled?: boolean
}) => {
    const color = deed.group ? GROUP_COLORS[deed.group] : undefined
    const industrial = deed.building?.label === "Teollisuus"
    return (
        <Box sx={portrait}>
            <Box sx={{ position: "absolute", inset: "0.55em", border: "0.07em solid #333", p: "0.45em", display: "flex", flexDirection: "column" }}>
                {color
                    ? (
                        <Box
                            sx={{
                                border: "0.07em solid #222",
                                backgroundColor: color.band,
                                color: color.text,
                                py: "0.25em",
                                textAlign: "center",
                                fontSize: "0.82em",
                                fontWeight: 700,
                                textTransform: "uppercase",
                            }}
                        >
                            {groupName}
                        </Box>
                    )
                    : (
                        <Box
                            sx={{
                                alignSelf: "center",
                                width: "2.4em",
                                height: "2.4em",
                                borderRadius: "0.3em",
                                backgroundColor: "#1e6fd1",
                                color: "#fff",
                                fontSize: "1.1em",
                                fontWeight: 800,
                                display: "grid",
                                placeItems: "center",
                            }}
                        >
                            P
                        </Box>
                    )}
                <Box
                    sx={{
                        my: "0.5em",
                        textAlign: "center",
                        fontWeight: 800,
                        // Long names such as Elektroniikkayhtiö would overflow the card
                        fontSize: deed.name.length > 15 ? "0.88em" : "1.05em",
                        textTransform: "uppercase",
                        lineHeight: 1.1,
                        overflowWrap: "anywhere",
                    }}
                >
                    {deed.name}
                </Box>
                <Box sx={{ fontSize: "0.66em" }}>
                    <Row label="Tontti maksaa" value={amount(deed.price)} />
                    {deed.building && <Row label={`${deed.building.label} maksaa`} value={amount(deed.building.price)} />}
                    {deed.parkingFee != null && (
                        <Row
                            label={
                                <>
                                    Pysäköintimaksu<br />(vain autonomistajille)
                                </>
                            }
                            value={amount(deed.parkingFee)}
                        />
                    )}
                    {deed.rent && <Section title="Vuokrat:" values={deed.rent} doubled={rentDoubled} />}
                    {deed.mortgage ? <Section title="Lainoitus:" values={deed.mortgage} /> : <Warning>Ei lainoitusta</Warning>}
                    {deed.buyBack
                        ? deed.building ? <Section title="Takaisinosto:" values={deed.buyBack} /> : (
                            <Box sx={{ mt: "0.45em" }}>
                                <Row label="Takaisinostoarvo" value={amount(deed.buyBack.unbuilt)} />
                            </Box>
                        )
                        : <Warning>Ei osteta takaisin</Warning>}
                </Box>
            </Box>
            {built && (
                <Box
                    title={industrial ? "Industrial plant" : "Building"}
                    sx={{
                        position: "absolute",
                        top: "0.15em",
                        right: "0.15em",
                        color: industrial ? "#111" : "#c62828",
                        "& svg": { fontSize: "1.9em", filter: "drop-shadow(0 0.05em 0.1em rgba(0,0,0,0.5))" },
                    }}
                >
                    {industrial ? <FactoryRounded /> : <HomeRounded />}
                </Box>
            )}
            {mortgaged && (
                // A mortgaged deed is turned over in the physical game; its back reads "LAINOITETTU" with the redemption value
                <Box
                    sx={{
                        position: "absolute",
                        left: 0,
                        right: 0,
                        bottom: 0,
                        py: "0.3em",
                        backgroundColor: "#1d4f9c",
                        color: "#fff",
                        textAlign: "center",
                        fontSize: "0.68em",
                        fontWeight: 700,
                    }}
                >
                    LAINOITETTU · Lunastusarvo {amount(built ? deed.redemption?.built : deed.redemption?.unbuilt)}
                </Box>
            )}
        </Box>
    )
}

/** Grey ornamental frame of shares and bonds */
const ornament = {
    backgroundColor: "#d5d6d3",
    backgroundImage: "radial-gradient(circle, #4a4a4a 0.12em, transparent 0.14em), radial-gradient(circle, #8a8a8a 0.1em, transparent 0.12em)",
    backgroundSize: "0.55em 0.55em",
    backgroundPosition: "0 0, 0.275em 0.275em",
} as const

const serif = { fontFamily: "'Times New Roman', Georgia, serif", fontWeight: 800 } as const

/** A share certificate ("osake") */
export const ShareCard = ({ share, board }: { share: Share; board: GameBoardData | null }) => {
    const group = board?.groups.find((item) => item.id === share.group)
    const capital = (board?.shares ?? []).filter((item) => share.group && item.group === share.group).reduce((sum, item) => sum + item.value, 0)
    return (
        <Box sx={{ ...portrait, ...ornament, p: "0.8em" }}>
            <Box
                sx={{
                    height: "100%",
                    boxSizing: "border-box",
                    border: "0.06em solid #333",
                    backgroundColor: "#cfdfcb",
                    p: "0.5em",
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "center",
                    textAlign: "center",
                }}
            >
                {group
                    ? (
                        <>
                            <Box sx={{ ...serif, fontSize: "2em", lineHeight: 1 }}>OSAKE</Box>
                            <Box sx={{ fontWeight: 700, fontSize: "0.78em", textTransform: "uppercase", mt: "0.2em" }}>{group.name}</Box>
                            <Box sx={{ fontSize: "0.5em" }}>(ruudut {group.properties.join(", ")})</Box>
                        </>
                    )
                    : (
                        <>
                            <Box sx={{ ...serif, fontSize: "1.2em", lineHeight: 1, mt: "0.4em" }}>RAHASTO-</Box>
                            <Box sx={{ ...serif, fontSize: "2em", lineHeight: 1 }}>OSAKE</Box>
                        </>
                    )}
                <Box sx={{ alignSelf: "stretch", borderTop: "0.06em solid #333", my: "0.4em" }} />
                <Box sx={{ fontWeight: 800, fontSize: "1.35em" }}>{amount(share.value)},–</Box>
                {group && (
                    <Box sx={{ fontSize: "0.5em" }}>
                        Kokonaisosakepääoma
                        <br />
                        {amount(capital)},–
                    </Box>
                )}
                <Box sx={{ fontWeight: 800, fontSize: "1.35em", mt: "0.2em" }}>{share.dividendPercent}%</Box>
                <Box sx={{ mt: "auto", alignSelf: "stretch", fontSize: "0.58em", textAlign: "left" }}>
                    <Row label="Osinko" value={amount(share.dividend)} />
                    <Row label="Takaisinostoarvo" value={amount(share.buyBack)} />
                </Box>
            </Box>
        </Box>
    )
}

/** A bond ("obligaatio") */
export const BondCard = ({ number }: { number: number }) => (
    <Box sx={{ ...landscape, ...ornament, p: "0.75em" }}>
        <Box
            sx={{
                height: "100%",
                boxSizing: "border-box",
                border: "0.06em solid #333",
                backgroundColor: "#d9dcd8",
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                justifyContent: "center",
                gap: "0.15em",
                color: "#2c3a33",
            }}
        >
            <Box sx={{ ...serif, fontSize: "1.6em", fontWeight: 700 }}>Obligaatio</Box>
            <Box sx={{ fontWeight: 700, fontSize: "1.05em" }}>Nro {number}</Box>
            <Box sx={{ fontWeight: 800, fontSize: "1.4em" }}>500,–</Box>
            <Box sx={{ fontSize: "0.6em" }}>Palautetaan voiton jälkeen.</Box>
        </Box>
    </Box>
)

/** A loan certificate ("lainatodistus") */
export const LoanCard = () => (
    <Box sx={{ ...landscape, p: "0.6em" }}>
        <Box
            sx={{
                height: "100%",
                boxSizing: "border-box",
                border: "0.3em dotted #6f6f6f",
                outline: "0.06em solid #6f6f6f",
                outlineOffset: "-0.55em",
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                justifyContent: "center",
            }}
        >
            <Box
                sx={{
                    fontFamily: "Rockwell, 'Roboto Slab', 'Courier New', serif",
                    fontWeight: 900,
                    color: "#6b2b2b",
                    fontSize: "2em",
                    lineHeight: 1.05,
                    textAlign: "center",
                }}
            >
                LAINA-
                <br />
                TODISTUS
            </Box>
            <Box sx={{ mt: "0.35em", color: "#1d4f9c", fontWeight: 800 }}>50 000,–</Box>
        </Box>
    </Box>
)

/** A car certificate ("autotodistus") */
export const CarCard = () => (
    <Box sx={{ ...landscape, p: "0.45em" }}>
        <Box
            sx={{
                height: "100%",
                boxSizing: "border-box",
                border: "0.18em solid #c62828",
                p: "0.45em 0.6em",
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                textAlign: "center",
            }}
        >
            <Box sx={{ ...serif, color: "#1f5ca8", fontSize: "1.45em", lineHeight: 1.05 }}>AUTO-TODISTUS</Box>
            <Box sx={{ fontWeight: 800, fontSize: "0.8em", mt: "0.3em" }}>Hinta 50 000,–</Box>
            <Box sx={{ fontSize: "0.5em", mt: "0.3em", textAlign: "justify" }}>
                Tämä kortti todistaa, että olet ostanut auton. Säilytä kortti edessäsi. Niin kauan kuin sinulla on hallussasi tämä todistus, saat jokaisella
                vuorollasi heittää kahta noppaa (poikkeus: pankin sisällä sekä autonomistajat että autottomat heittävät vain yhtä noppaa).
            </Box>
            <Box sx={{ fontWeight: 800, fontSize: "0.7em", mt: "auto" }}>Takaisinostoarvo 25 000,–</Box>
        </Box>
    </Box>
)

/** Tooltip styling for peeks that show cards: no bubble, just the cards */
export const cardPeekSlotProps = { tooltip: { sx: { p: 0, m: 0, maxWidth: "none", backgroundColor: "transparent", boxShadow: "none", fontSize: 13 } } }

/** Cards laid out side by side in a peek */
export const CardRow = ({ children }: { children: ReactNode }) => (
    <Stack direction="row" spacing={1.25} useFlexGap sx={{ flexWrap: "wrap", maxWidth: "min(760px, 90vw)", p: 1 }}>{children}</Stack>
)
