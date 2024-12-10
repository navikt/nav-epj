import AppPicker from '@/components/app-picker'
import { Suspense } from 'react'
import Consultation from '@/components/consultation'
import Workspace from '@/components/workspace'

export default function Home() {
    return (
        <div className="flex h-full">
            <div className="min-w-72 w-72 max-w-72 p-4 pt-0">
                <div id="patient-section" className="min-h-48 mt-2">
                    <h3 className="p-2 font-bold">Your patients</h3>
                    <Suspense fallback={<span className="loader"></span>}>
                        <Consultation />
                    </Suspense>
                </div>
                <div className="mt-4">
                    <h3 className="p-2 font-bold">SMART Apps</h3>
                    <Suspense fallback={<span className="loader"></span>}>
                        <AppPicker />
                    </Suspense>
                </div>
            </div>
            <div className="w-full h-full bg-white text-black">
                <Workspace />
            </div>
        </div>
    )
}
