import React from 'react'
import PatientPicker from '@/components/patient-picker'
import PatientPicked from '@/components/patient-picked'
import { cookies } from 'next/headers'

const Consultation = async () => {
    const cookieStore = await cookies()
    const patientSet = cookieStore.get('patient-id')?.value

    if (patientSet) {
        return (
            <>
                <h3 className="p-2 font-bold">Patient</h3>
                <PatientPicked />
            </>
        )
    } else {
        return (
            <>
                <h3 className="p-2 font-bold">Your patients</h3>
                <PatientPicker />
            </>
        )
    }
}

export default Consultation
