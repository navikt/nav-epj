import React from 'react'
import { Heading } from '@navikt/ds-react'
import Image from 'next/image'

import icon from '../app/favicon.ico'

const Header = () => {
    return (
        <div className=" w-full flex items-center justify-between">
            <div className="p-2 flex">
                <div className="text-3xl p-2 -mt-0.5">
                    <Image src={icon} alt="" width="42" height="42" unoptimized />
                </div>
                <div>
                    <Heading level="1" size="medium">
                        Nav Test EPJ
                    </Heading>
                    <div className="text-xs">Et fake pasientjournalsystem med testdata fra Dolly</div>
                </div>
            </div>
            <div className="p-2 text-right">
                {/*TODO: Dette må komme fra en eller annen EPJ session*/}
                <div className="font-bold">Magmar Koman</div>
                <div className="text-sm text-gray-200">Nav EPJ v0.42</div>
            </div>
        </div>
    )
}

export default Header
