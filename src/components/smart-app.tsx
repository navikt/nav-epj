import React from 'react'
import CloseApp from './smart-app-close-app'

const SmartApp = ({ url, appName }: { url: string; appName: string }) => {
    return (
        <div className="flex h-full flex-col justify-center items-center">
            <iframe
                className="w-full h-full rounded"
                src={url}
                sandbox="allow-scripts allow-same-origin allow-forms"
                referrerPolicy="no-referrer"
                loading="lazy"
            />
            <div className="h-10 w-full flex items-center justify-between border-t border-t-border-subtle">
                <div className="py-2 px-4">
                    SMART App: <span>{appName}</span>
                </div>
                {process.env.NODE_ENV !== 'production' && (
                    <div className="text-xs text-text-subtle grow">
                        <div>Debug launch URL</div>
                        <pre>{url}</pre>
                    </div>
                )}
                <div className="font-black">
                    <CloseApp />
                </div>
            </div>
        </div>
    )
}

export default SmartApp
