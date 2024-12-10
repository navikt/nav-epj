import React from 'react'
import PatientPicker from '@/components/patient-picker'
import PatientPicked from '@/components/patient-picked'
import { cookies } from 'next/headers'

const Consultation = async () => {
    const cookieStore = await cookies()
    const patientSet = cookieStore.get('patient-id')

    if (patientSet) {
        return <PatientPicked />
    } else {
        return <PatientPicker />
    }
}

export default Consultation
