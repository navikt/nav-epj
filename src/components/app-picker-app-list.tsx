'use client'

import React, { ReactElement } from 'react'
import { configuredApps, useSelectedApp } from '@/state/apps'

type Props = {
    hasConsultation: boolean
}

function AvailableAppsList({ hasConsultation }: Props): ReactElement {
    const [selectedApp, setApp] = useSelectedApp()

    return (
        <>
            {configuredApps.map((app) => (
                <button
                    key={app.clientId}
                    className={`flex flex-col p-2 items-start border rounded border-gray-300 enabled:hover:bg-blue-950 disabled:opacity-60 ${app.clientId === selectedApp?.clientId && 'bg-blue-950'}`}
                    disabled={!hasConsultation}
                    onClick={() => {
                        setApp(app.clientId)
                    }}
                >
                    <span className="text-lg">{app.name}</span>
                    <span className="text-sm text-gray-200">{app.clientId}</span>
                </button>
            ))}
        </>
    )
}

export default AvailableAppsList
