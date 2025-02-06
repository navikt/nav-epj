'use client'

import React from 'react'
import { unsetPatient } from '@/components/patient-actions'
import { useSelectedApp } from '@/state/apps'

const PatientPicked = () => {
    const [, setApp] = useSelectedApp()

    return (
        <div className="w-full h-full flex flex-col">
            <div className="text-xs text-gray-200">Active consultation</div>
            {/*TODO: Dette må komme fra en eller annen EPJ session*/}
            <div>21037712323</div>
            <div className="text-xs text-gray-200">Name</div>
            {/*TODO: Dette må komme fra en eller annen EPJ session*/}
            <div>Espen Eksempel</div>

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
