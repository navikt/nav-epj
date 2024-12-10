import React from 'react'

const SmartApp = async ({ url, appName }: { url: string; appName: string }) => {
    return (
        <div className="flex h-full flex-col justify-center items-center">
            <iframe
                className="w-full h-full rounded"
                src={url}
                sandbox="allow-scripts allow-same-origin"
                referrerPolicy="no-referrer"
                loading="lazy"
            >
                <span className="loader"></span>
            </iframe>
            <div className="h-10 w-full flex items-center justify-between">
                <div className="py-2 px-4">
                    SMART App: <span>{appName}</span>
                </div>
                <div>
                    <button className="py-2 px-4 hover:bg-gray-200">TODO Lukk app</button>
                </div>
            </div>
        </div>
    )
}

export default SmartApp
