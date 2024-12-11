import type { Metadata } from 'next'
import './globals.css'
import Header from '@/components/header'
import Footer from '@/components/footer'
import React from 'react'
import { NuqsAdapter } from 'nuqs/adapters/next/app'

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
                <Header />
                <NuqsAdapter>{children}</NuqsAdapter>
                <Footer />
            </body>
        </html>
    )
}
