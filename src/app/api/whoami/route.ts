export function GET(req: Request) {
    const token = req.headers.get('Authorization')?.replace('Bearer ', '')
    const parts = token?.split('.')

    return Response.json({
        header: JSON.parse(atob(parts?.[0] || '')),
        payload: JSON.parse(atob(parts?.[1] || '')),
        signature: parts?.[2],
    })
}
