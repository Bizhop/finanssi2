import { type ReactNode, useId, useState } from "react"
import { Box, ButtonBase, Paper, Stack, Tab, Tabs, Typography } from "@mui/material"
import ExpandMoreIcon from "@mui/icons-material/ExpandMore"

export type GameWindowTab = {
    label: string
    content: (state: { expanded: boolean; active: boolean }) => ReactNode
}

type GameWindowProps = {
    title: string
    tabs: GameWindowTab[]
    defaultExpanded?: boolean
    fill?: boolean
}

export const GameWindow = ({ title, tabs, defaultExpanded = true, fill = false }: GameWindowProps) => {
    const [expanded, setExpanded] = useState(defaultExpanded)
    const [activeTab, setActiveTab] = useState(0)
    const id = useId()
    const bodyId = `${id}-body`
    return (
        <Paper
            variant="outlined"
            sx={{
                minWidth: 0,
                minHeight: fill && expanded ? { xs: 360, lg: 0 } : 0,
                overflow: "hidden",
                display: "flex",
                flexDirection: "column",
                flex: fill && expanded ? 1 : "0 0 auto",
            }}
        >
            <ButtonBase
                onClick={() => setExpanded((value) => !value)}
                aria-expanded={expanded}
                aria-controls={bodyId}
                aria-label={`${expanded ? "Minimize" : "Expand"} ${title}`}
                sx={{ flex: "0 0 auto", width: "100%", p: 1, justifyContent: "space-between", textAlign: "left" }}
            >
                <Typography sx={{ fontWeight: 700 }}>{title}</Typography>
                <ExpandMoreIcon sx={{ transform: expanded ? "rotate(180deg)" : "none", transition: "transform 150ms" }} />
            </ButtonBase>
            <Box
                id={bodyId}
                hidden={!expanded}
                inert={!expanded}
                sx={{ minHeight: 0, flex: 1, display: expanded ? "flex" : "none", flexDirection: "column" }}
            >
                <Tabs value={activeTab} onChange={(_, value: number) => setActiveTab(value)} variant="fullWidth" aria-label={title}>
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
                                {tab.content({ expanded: expanded && active, active })}
                            </Stack>
                        )
                    })}
                </Box>
            </Box>
        </Paper>
    )
}
