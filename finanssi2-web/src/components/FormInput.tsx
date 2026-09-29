import React from "react"
import { Alert, Stack, TextField } from "@mui/material"
import { useController } from "react-hook-form"

export function InputField({control, name, label, type, error}) {
  const { field } = useController({ name, control })

  return (
    <Stack direction="column" spacing={1}>
      <TextField {...field} label={label} type={type} error={error !== undefined} />
      {error && <Alert severity="error">{error.message}</Alert>}
    </Stack>
  )
}
