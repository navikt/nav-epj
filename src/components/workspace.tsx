import React from 'react'
import SmartApp from '@/components/smart-app'
import NoApp from '@/components/no-app'
// import { useQueryState } from 'nuqs'

const Workspace = () => {
    // const [app, setApp] = useQueryState('app', { defaultValue: '', clearOnDefault: true })

    if (true) {
        return (
            <SmartApp
                url="https://nav-on-fhir.ekstern.dev.nav.no/launch?iss=https%3A%2F%2Flaunch.smarthealthit.org%2Fv%2Fr4%2Ffhir&launch=WzAsIjVkMDhmN2RhLTVhNzgtNGQzNS1hZmVjLTlkMmMyYzAyNDVlZSIsIjYwYzlmZTYzLWQ5ZTYtNGU1ZS04ZDVjLWY5MWI3ZmM3NTQxOSIsIkFVVE8iLDAsMCwwLCJvcGVuaWQgcHJvZmlsZSBsYXVuY2ggZmhpclVzZXIgcGF0aWVudC8qLiogdXNlci8qLiogb2ZmbGluZV9hY2Nlc3MiLCJodHRwczovL25hdi1vbi1maGlyLmVrc3Rlcm4uZGV2Lm5hdi5ubyIsIk5BVl9TTUFSVF9vbl9GSElSX2V4YW1wbGUiLCIiLCIiLCIiLCIiLDAsMSwiIl0"
                appName="Test"
            />
        )
    }
    return <NoApp />
}

export default Workspace
