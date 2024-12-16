import { useQueryState } from 'nuqs'

interface App {
    name: string
    clientId: string
    url: string
}

export const configuredApps = [
    {
        name: 'Sykmeldinger',
        clientId: 'syk-inn' as const,
        url: 'https://www.ekstern.dev.nav.no/samarbeidspartner/sykmelding/fhir',
    },
    {
        name: 'Sykmeldinger (demo FHIR)',
        clientId: 'syk-inn-demo' as const,
        url: 'https://syk-inn.ekstern.dev.nav.no/samarbeidspartner/sykmelding/fhir/launch?iss=https://syk-inn.ekstern.dev.nav.no/samarbeidspartner/sykmelding/api/mocks/fhir&launch=local-dev-id',
    },
    {
        name: "Leo's SMART Probe",
        clientId: 'NAV_SMART_on_FHIR_example' as const,
        url: 'https://nav-on-fhir.ekstern.dev.nav.no',
    },
    {
        name: 'Localhost Dev',
        clientId: 'localhost' as const,
        url: 'http://localhost:3000/fhir',
    },
]

const NAV_FHIR_URL = 'https://fhir.ekstern.dev.nav.no'

type AvailableApps = (typeof configuredApps)[number]['clientId']

export function useSelectedApp(): [App | null, (app: AvailableApps | null) => Promise<URLSearchParams>] {
    const [app, setApp] = useQueryState('app', { defaultValue: '', clearOnDefault: true })

    const setOnlyApplicableApp = async (app: AvailableApps | null) => setApp(app)
    const selectedApp = configuredApps.find((a) => a.clientId === app)

    return [selectedApp ?? null, setOnlyApplicableApp]
}

export function getLaunchURL(app: App | AvailableApps): string {
    const selectedApp = typeof app === 'object' ? app : configuredApps.find((a) => a.clientId === app)

    if (!selectedApp) {
        throw new Error(`App with clientId ${app} not found`)
    }

    // This already has iss and launch params
    if (app === 'syk-inn-demo') {
        return selectedApp.url
    }

    return `${selectedApp.url}/launch?iss=${NAV_FHIR_URL}&launch=todo-better-launch-id`
}
