import React from 'react'
import {Heading} from "@navikt/ds-react";

const Header = () => {
    return (
        <div className=" w-full h-16 flex items-center justify-between">
            <div className="p-4 flex">
                <div className="text-3xl p-2 mr-2">🔥</div>
                <div>
                    <Heading level="1" size="medium">Nav EPJs</Heading>
                    <div className="text-xs">Et fake journalsystem med testdata fra Dolly</div>
                </div>
            </div>
            <div className="p-4 text-right">
                <div className="font-bold">TODO USER NAME</div>
                <div className="text-sm text-gray-200">TODO HPR</div>
            </div>
        </div>
    )
}

export default Header
