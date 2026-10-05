import { Alert, Stack, TextField } from "@mui/material"
import { Control, FieldError, FieldPath, FieldValues, useController } from "react-hook-form"

type InputFieldProps<T extends FieldValues> = {
    control: Control<T>
    name: FieldPath<T>
    label: string
    type: string
    size?: "small" | "medium"
    error?: FieldError
}

export function InputField<T extends FieldValues>({ control, name, label, type, size = "medium", error }: InputFieldProps<T>) {
    const { field } = useController({ name, control })

    return (
        <Stack direction="column" spacing={1}>
            <TextField {...field} label={label} type={type} size={size} error={error !== undefined} fullWidth />
            {error && <Alert severity="error">{error.message}</Alert>}
        </Stack>
    )
}
