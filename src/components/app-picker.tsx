import React from 'react'
import { cookies } from 'next/headers'
import AvailableAppsList from '@/components/app-picker-app-list'

const AppPicker = async () => {
    await new Promise((resolve) => {
        setTimeout(resolve, 100) // TODO fake wait for API
    })

    const cookieStore = await cookies()
    const patientId = cookieStore.get('patient-id')?.value

    return (
        <div id="apps-picker" className="flex flex-col gap-3 overflow-auto">
            <AvailableAppsList hasConsultation={!!patientId} />
        </div>
    )
}

export default AppPicker
