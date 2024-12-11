import type { Config } from 'tailwindcss'

import akselWind from '@navikt/ds-tailwind'

export default {
    content: ['./src/**/*.{js,ts,jsx,tsx,mdx}'],
    presets: [akselWind],
    plugins: [],
} satisfies Config
