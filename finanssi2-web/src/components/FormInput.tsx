import { Alert, Stack, TextField } from "@mui/material"
import { Control, FieldError, FieldPath, FieldValues, useController } from "react-hook-form"

type InputFieldProps<T extends FieldValues> = {
    control: Control<T>
    name: FieldPath<T>
    label: string
    type: string
    error?: FieldError
}

export function InputField<T extends FieldValues>({ control, name, label, type, error }: InputFieldProps<T>) {
    const { field } = useController({ name, control })

    return (
        <Stack direction="column" spacing={1}>
            <TextField {...field} label={label} type={type} error={error !== undefined} fullWidth />
            {error && <Alert severity="error">{error.message}</Alert>}
        </Stack>
    )
}
