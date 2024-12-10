import React from 'react'
import { cookies } from 'next/headers'

interface Patient {
    name: string
    id: string
}

const PatientPicker = async () => {
    await new Promise((resolve) => {
        setTimeout(resolve, 1500) // Fake wait for API
    })

    const patients: Patient[] = [
        { name: 'Ola Nordmann', id: '45847100951' },
        { name: 'Kari Karisdottir', id: '65927600603' },
    ]

    return (
        <div className="flex flex-col gap-3">
            <form
                className="flex flex-col gap-3"
                action={async (formData: FormData) => {
                    'use server'

                    const rawFormData = {
                        patientId: formData.get('fnr')?.toString(),
                    }

                    const cookieStore = await cookies()

                    if (typeof rawFormData.patientId !== 'string') {
                        throw new Error('Patient ID is required!')
                    }

                    cookieStore.set('patient-id', rawFormData.patientId, { httpOnly: true, secure: true })
                }}
            >
                <select
                    defaultValue=""
                    id="patientSelect"
                    name="fnr"
                    className="border rounded border-gray-300 bg-transparent p-2"
                    required
                >
                    <option value="" disabled>
                        No patient selected
                    </option>
                    {patients.map((p) => (
                        <option key={p.id} value={p.id}>
                            {p.name}
                        </option>
                    ))}
                </select>

                <button type="submit" className="mt-3 p-2 bg-blue-300 text-black rounded hover:bg-blue-600">
                    Start consultation
                </button>
            </form>
        </div>
    )
}

export default PatientPicker
