import { createContext, useContext } from 'react'

// Set by Field so a custom control such as Select can take the id of the Field label.
export const FieldContext = createContext<{ id: string; describedBy?: string; invalid: boolean } | null>(null)
export const useFieldControl = () => useContext(FieldContext)
