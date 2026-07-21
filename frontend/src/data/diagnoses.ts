

export type NavEpjDiagnose = {
    kode: string;
    beskrivelse: string;
    diagnosesystem: 'ICPC2' | 'ICD10'
}

export const epjDiagnoser: NavEpjDiagnose[] = [
    {
        kode: 'A01',
        beskrivelse: 'Smerte generell/flere steder',
        diagnosesystem: 'ICPC2'
    },
    {
        kode: 'A02',
        beskrivelse: 'Frysninger',
        diagnosesystem: 'ICPC2'
    },
    {
        kode: 'A03',
        beskrivelse: 'Feber',
        diagnosesystem: 'ICPC2'
    },
    {
        kode: 'B00.0',
        beskrivelse: 'Eczema herpeticum',
        diagnosesystem: 'ICD10',
    },
    {
        kode: 'B03',
        beskrivelse: 'Kopper',
        diagnosesystem: 'ICD10'
    }
]