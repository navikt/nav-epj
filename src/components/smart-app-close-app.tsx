'use client'

import React, { ReactElement } from 'react'
import { useSelectedApp } from '@/state/apps'

function CloseApp(): ReactElement {
    const [, setApp] = useSelectedApp()

    return (
        <button
            className="py-2 px-4 hover:bg-gray-200"
            onClick={() => {
                setApp(null)
            }}
        >
            Lukk app
        </button>
    )
}

export default CloseApp
