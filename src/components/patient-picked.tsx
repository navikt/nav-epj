'use client'

import React from 'react'
import { unsetPatient } from '@/components/patient-actions'
import { useSelectedApp } from '@/state/apps'

const PatientPicked = () => {
    const [, setApp] = useSelectedApp()

    return (
        <div className="w-full h-full flex flex-col">
            <div className="text-xs text-gray-200">Active consultation</div>
            <div>TODO FNR</div>
            <div className="text-xs text-gray-200">Name</div>
            <div>TODO NAME</div>

            <button
                className="mt-3 p-2 bg-red-500 text-white rounded hover:bg-red-600"
                onClick={async () => {
                    await unsetPatient()
                    await setApp(null)
                }}
            >
                Close consultation
            </button>
        </div>
    )
}

export default PatientPicked
