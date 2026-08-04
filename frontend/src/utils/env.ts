type EnvUrls = {
    [key in 'localhost' | 'dev' | 'prod']: {
        sykInnUrl: string
        validatorUrl: string
    }
}

const envUrls: EnvUrls = {
    localhost: {
        sykInnUrl: "http://localhost:3000/fhir/launch",
        validatorUrl: "http://localhost:5174/launch"
    },
    dev: {
        sykInnUrl: "",
        validatorUrl: "https://nav-on-fhir.ekstern.dev.nav.no/launch"
    },
    prod: {
        sykInnUrl: "",
        validatorUrl: ""
    }
}

const getEnv = () => {
    if (window.location.hostname === 'localhost') {
        return 'localhost'
    } else if (window.location.hostname.includes('dev.nav.no')) {
        return 'dev'
    } else {
        return 'prod'
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