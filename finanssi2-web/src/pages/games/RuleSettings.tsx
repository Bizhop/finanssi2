import { FormControl, InputLabel, MenuItem, Select, Typography } from "@mui/material"

const RECOMMENDED = "(recommended)"
const ORIGINAL = "(original)"

/** The game's rule options: the recommended ones are the defaults, the original printed rules stay available for legacy play */
export const RuleSettings = ({ loanLimit, setLoanLimit, minimumBid, setMinimumBid, meetingRule, setMeetingRule }: {
    loanLimit: string
    setLoanLimit: (value: string) => void
    minimumBid: string
    setMinimumBid: (value: string) => void
    meetingRule: string
    setMeetingRule: (value: string) => void
}) => (
    <>
        <FormControl fullWidth>
            <InputLabel id="loan-limit-label">Bank loans</InputLabel>
            <Select labelId="loan-limit-label" label="Bank loans" value={loanLimit} onChange={(event) => setLoanLimit(event.target.value)}>
                <MenuItem value="UNLIMITED">Unlimited {RECOMMENDED}</MenuItem>
                <MenuItem value="OFFICIAL">6 in total {ORIGINAL}</MenuItem>
            </Select>
        </FormControl>
        <FormControl fullWidth>
            <InputLabel id="sale-minimum-label">Forced sale minimum bid</InputLabel>
            <Select labelId="sale-minimum-label" label="Forced sale minimum bid" value={minimumBid} onChange={(event) => setMinimumBid(event.target.value)}>
                <MenuItem value="HALF_NOMINAL_PRICE">Half the nominal price {RECOMMENDED}</MenuItem>
                <MenuItem value="NONE">No minimum {ORIGINAL}</MenuItem>
            </Select>
        </FormControl>
        <FormControl fullWidth>
            <InputLabel id="meeting-rule-label">Shareholders' meetings</InputLabel>
            <Select labelId="meeting-rule-label" label="Shareholders' meetings" value={meetingRule} onChange={(event) => setMeetingRule(event.target.value)}>
                <MenuItem value="ALL_ASSETS_BOUGHT">Whole group bought from the bank {RECOMMENDED}</MenuItem>
                <MenuItem value="ANY_OTHER_OWNER">Any group with another owner {ORIGINAL}</MenuItem>
            </Select>
        </FormControl>
        <Typography variant="caption" color="text.secondary">
            Recommended rules are the defaults. Original rules follow the printed game and are kept for legacy play, though some of them play poorly.
        </Typography>
    </>
)
