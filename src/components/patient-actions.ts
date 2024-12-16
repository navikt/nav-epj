'use server'

import { cookies } from 'next/headers'

export async function unsetPatient(): Promise<void> {
    const cookieStore = await cookies()
    cookieStore.delete('patient-id')
}
