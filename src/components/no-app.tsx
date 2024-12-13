import React from 'react'
import Image from 'next/image'

const NoApp = () => {
    return (
        <div id="inner-frame" className="w-full h-full rounded-l-3xl overflow-hidden">
            <div className="w-full h-full flex justify-center items-center">
                <div className="flex flex-col items-center">
                    <Image
                        src="/doctor.webp"
                        alt="Doctor, doctoring at the doctors office"
                        height="420"
                        width="420"
                        unoptimized
                    />
                    <p className="font-bold">← Velg en pasient i pasientvelgeren</p>
                </div>
            </div>
        </div>
    )
}

export default NoApp
