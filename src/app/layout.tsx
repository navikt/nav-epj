import type { Metadata } from 'next'
import './globals.css'
import Header from '@/components/header'
import Footer from '@/components/footer'
import React from 'react'
import { NuqsAdapter } from 'nuqs/adapters/next/app'
import BaseGrid from '@/components/grid/base-grid'

export const metadata: Metadata = {
    title: 'NAV (fake) EPJ',
    description: 'Fake EPJ from NAV with ❤',
}

export default function RootLayout({
    children,
}: Readonly<{
    children: React.ReactNode
}>) {
    return (
        <html lang="en">
            <body>
                <BaseGrid header={<Header />} footer={<Footer />}>
                    <NuqsAdapter>{children}</NuqsAdapter>
                </BaseGrid>
            </body>
        </html>
    )
}
