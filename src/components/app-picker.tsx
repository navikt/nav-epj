import React from 'react'
import { cookies } from 'next/headers'

interface App {
    name: string
    clientId: string
    url: string
}

const AppPicker = async () => {
    await new Promise((resolve) => {
        setTimeout(resolve, 300) // TODO fake wait for API
    })

    const cookieStore = await cookies()
    const patientId = cookieStore.get('patient-id')?.value

    const apps: App[] = [
        {
            name: 'Sykmeldinger',
            clientId: 'syk-inn',
            url: 'https://www.ekstern.dev.nav.no/samarbeidspartner/sykmelding/fhir',
        },
        {
            name: "Leo's SMART Probe",
            clientId: 'NAV_SMART_on_FHIR_example',
            url: 'https://nav-on-fhir.ekstern.dev.nav.no',
        },
        {
            name: 'Localhost Dev',
            clientId: 'localhost',
            url: 'http://localhost:3000/fhir',
        },
    ]

    return (
        <div id="apps-picker" className="flex flex-col gap-3">
            <p className="text-xs mb-2 ml-2">Apps require an active consultation</p>
            {apps.map((app) => (
                <button
                    key={app.clientId}
                    className="flex flex-col p-2 items-start border rounded border-gray-300 enabled:hover:bg-blue-950 disabled:opacity-60"
                    disabled={!patientId}
                >
                    <span className="text-lg">{app.name}</span>
                    <span className="text-sm text-gray-200">{app.clientId}</span>
                </button>
            ))}
        </div>
    )
}

export default AppPicker
