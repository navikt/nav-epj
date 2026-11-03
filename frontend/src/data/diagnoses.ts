

export type NavEhrDiagnosis = {
    code: string;
    description: string;
    diagnosisSystem: 'ICPC2' | 'ICD10'
}

export const ehrDiagnoses: NavEhrDiagnosis[] = [
    {
        code: 'A01',
        description: 'Smerte generell/flere steder',
        diagnosisSystem: 'ICPC2'
    },
    {
        code: 'A02',
        description: 'Frysninger',
        diagnosisSystem: 'ICPC2'
    },
    {
        code: 'A03',
        description: 'Feber',
        diagnosisSystem: 'ICPC2'
    },
    {
        code: 'B00.0',
        description: 'Eczema herpeticum',
        diagnosisSystem: 'ICD10',
    },
    {
        code: 'B03',
        description: 'Kopper',
        diagnosisSystem: 'ICD10'
    }
]