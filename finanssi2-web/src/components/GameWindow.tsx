import { useId, useState, type ReactNode } from "react"
import { Box, ButtonBase, Paper, Stack, Typography } from "@mui/material"
import ExpandMoreIcon from "@mui/icons-material/ExpandMore"

type GameWindowProps = {
    title: string
    children: ReactNode
    defaultExpanded?: boolean
}

export const GameWindow = ({ title, children, defaultExpanded = true }: GameWindowProps) => {
    const [expanded, setExpanded] = useState(defaultExpanded)
    const bodyId = useId()
    return (
        <Paper variant="outlined" sx={{ minWidth: 0, overflow: "hidden" }}>
            <ButtonBase
                onClick={() => setExpanded((value) => !value)}
                aria-expanded={expanded}
                aria-controls={bodyId}
                aria-label={`${expanded ? "Minimize" : "Expand"} ${title}`}
                sx={{ width: "100%", p: 1.5, justifyContent: "space-between", textAlign: "left" }}
            >
                <Typography sx={{ fontWeight: 700 }}>{title}</Typography>
                <ExpandMoreIcon sx={{ transform: expanded ? "rotate(180deg)" : "none", transition: "transform 150ms" }} />
            </ButtonBase>
            <Box id={bodyId} hidden={!expanded} inert={!expanded} sx={{ p: 1.5, pt: 0, maxHeight: "min(55vh, 560px)", minHeight: 0, display: expanded ? "flex" : "none", flexDirection: "column" }}>
                <Stack sx={{ minHeight: 0, flex: 1 }}>{children}</Stack>
            </Box>
        </Paper>
    )
}
