import AppPicker from '@/components/app-picker'
import React, { Suspense } from 'react'
import Consultation from '@/components/consultation'
import Workspace from '@/components/workspace'

export default function Home() {
    return (
        <>
            <div id="grid-sidebar" className="flex flex-col p-4 pt-0">
                <div id="patient-section" className="shrink-0 min-h-48 mt-2">
                    <Suspense fallback={<span className="loader"></span>}>
                        <Consultation />
                    </Suspense>
                </div>
                <div className="overflow-auto">
                    <h3 className="p-2 font-bold">SMART Apps</h3>
                    <p className="text-xs mb-4 ml-2">Apps require an active consultation</p>
                    <Suspense fallback={<span className="loader"></span>}>
                        <AppPicker />
                    </Suspense>
                </div>
            </div>
            <div
                id="grid-content"
                className="shrink w-full h-full rounded-l-3xl bg-white text-text-default overflow-hidden"
            >
                <Suspense fallback={<span className="loader"></span>}>
                    <Workspace />
                </Suspense>
            </div>
        </>
    )
}
