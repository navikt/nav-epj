'use client'

import React from 'react'
import SmartApp from '@/components/smart-app'
import NoApp from '@/components/no-app'
import { useSelectedApp } from '@/state/apps'

const Workspace = () => {
    const [selectedApp] = useSelectedApp()

    if (selectedApp != null) {
        return <SmartApp url={selectedApp.url} appName={selectedApp.name} />
    }
    return <NoApp />
}

export default Workspace
