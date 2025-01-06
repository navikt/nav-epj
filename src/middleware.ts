import { NextRequest, NextResponse } from 'next/server'

export function middleware(request: NextRequest): NextResponse {
    const nextUrl = request.nextUrl
    const patientId = request.cookies.get('patient-id')?.value
    const appParam = nextUrl.searchParams.get('app')

    if (patientId == null && appParam != null) {
        nextUrl.searchParams.delete('app')
        return NextResponse.redirect(nextUrl)
    }

    return NextResponse.next()
}

export const config = {
    matcher: ['/((?!api|_next/static|_next/image|favicon.ico).*)'],
}
