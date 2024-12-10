import React from 'react'

const Footer = () => {
    return (
        <div className=" w-full h-8 flex items-center justify-between px-4">
            <div>NavEPJ © 2024 har ingen ekte data. Det er et fiktivt EPJ-system med kun test data fra Dolly.</div>
            <div>
                <a className="text-xs underline" href="https://github.com/navikt/nav-fhir-server" target="_blank">
                    Kildekode på Github
                </a>
            </div>
        </div>
    )
}

export default Footer
