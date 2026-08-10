type EnvUrls = {
    [key in 'localhost' | 'dev']: {
        sykInnUrl: string
        validatorUrl: string
    }
}

const envUrls: EnvUrls = {
    localhost: {
        sykInnUrl: "http://localhost:3000/fhir/launch",
        validatorUrl: "http://localhost:3001/launch"
    },
    dev: {
        sykInnUrl: "https://www.ekstern.dev.nav.no/samarbeidspartner/sykmelding/fhir/launch",
        validatorUrl: "https://nav-on-fhir.ekstern.dev.nav.no/launch"
    },
}

const getEnv = () => {
    if (window.location.hostname === 'localhost') {
        return 'localhost'
    } else if (window.location.hostname.includes('dev.nav.no')) {
        return 'dev'
    } else {
        throw new Error('Unknown environment')
    }
}

export const getSykInnUrl = () => {
    const env = getEnv()
    return envUrls[env].sykInnUrl
}

export const getValidatorUrl = () => {
    const env = getEnv()
    return envUrls[env].validatorUrl
}