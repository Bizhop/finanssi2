import { type ReactNode, useId, useState } from "react"
import { Box, Paper, Stack, Tab, Tabs } from "@mui/material"

export type GameWindowTab = {
    label: string
    content: (state: { expanded: boolean; active: boolean }) => ReactNode
}

type GameWindowProps = {
    tabs: GameWindowTab[]
    fill?: boolean
}

export const GameWindow = ({ tabs, fill = false }: GameWindowProps) => {
    const [activeTab, setActiveTab] = useState(0)
    const id = useId()
    return (
        <Paper
            variant="outlined"
            sx={{
                minWidth: 0,
                minHeight: fill ? { xs: 360, xl: 0 } : 0,
                overflow: "hidden",
                display: "flex",
                flexDirection: "column",
                flex: fill ? 1 : "0 0 auto",
            }}
        >
            <Tabs value={activeTab} onChange={(_, value: number) => setActiveTab(value)} variant="fullWidth" aria-label="Game activity">
                {tabs.map((tab, index) => <Tab key={tab.label} label={tab.label} id={`${id}-tab-${index}`} aria-controls={`${id}-panel-${index}`} />)}
            </Tabs>
            <Box sx={{ flex: 1, minHeight: 0, position: "relative", p: 1 }}>
                {tabs.map((tab, index) => {
                    const active = activeTab === index
                    return (
                        <Stack
                            key={tab.label}
                            id={`${id}-panel-${index}`}
                            role="tabpanel"
                            aria-labelledby={`${id}-tab-${index}`}
                            hidden={!active}
                            inert={!active}
                            sx={{ position: active ? "relative" : "absolute", inset: 0, display: active ? "flex" : "none", minHeight: 0, height: "100%" }}
                        >
                            {tab.content({ expanded: active, active })}
                        </Stack>
                    )
                })}
            </Box>
        </Paper>
    )
}
