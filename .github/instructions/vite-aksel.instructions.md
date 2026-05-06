---
applyTo: "frontend/**/*.{tsx,ts}"
---

# Frontend Development (Vite + React 19 + Aksel)

## Stack

- **Bundler**: Vite
- **Framework**: React 19 (SPA, client-rendered)
- **Routing**: TanStack Router (file-based routes in `frontend/src/routes/`)
- **Design System**: Nav Aksel (`@navikt/ds-react` v8+)
- **Validation**: Zod
- **Package Manager**: Yarn 4

## Spacing Rules

**CRITICAL**: Always use Aksel spacing tokens via layout primitives, never Tailwind padding/margin.

### ✅ Correct

```tsx
import { Box, VStack, HStack, HGrid } from "@navikt/ds-react";

<Box paddingBlock="space-16" paddingInline="space-24">
  <VStack gap="4">
    <Component1 />
    <Component2 />
  </VStack>
</Box>

// Responsive
<Box padding={{ xs: "space-12", md: "space-24" }}>
  <HGrid columns={{ xs: 1, md: 2, lg: 3 }} gap="4">
    {items.map(item => <Card key={item.id} {...item} />)}
  </HGrid>
</Box>
```

### ❌ Incorrect

```tsx
<div className="p-4 md:p-6">   // Tailwind padding
<div className="mx-4 my-2">    // Tailwind margin
<Box padding="4">              // Missing space- prefix
```

## Spacing Tokens

Use with `space-` prefix: `space-4` (4px), `space-8`, `space-12`, `space-16`, `space-20`, `space-24`, `space-32`, `space-40`.

## Routing (TanStack Router)

```tsx
import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/patient/$patientId")({
  component: PatientDetail,
});

function PatientDetail() {
  const { patientId } = Route.useParams();
  // ...
}
```

## Data Fetching

Use standard `fetch` against backend FHIR API. Data flows through `/fhir/*` endpoints proxied by Vite dev server:

```tsx
const response = await fetch("/fhir/Patient/" + patientId);
const patient: FhirPatient = await response.json();
```

## Component Patterns

### Typography

```tsx
import { Heading, BodyShort, Label } from "@navikt/ds-react";

<Heading size="large" level="1">Title</Heading>
<BodyShort>Regular text</BodyShort>
<BodyShort weight="semibold">Bold text</BodyShort>
```

### Layout

```tsx
import { Box, VStack, HStack, HGrid, Spacer, InternalHeader } from "@navikt/ds-react";

<VStack gap="4">...</VStack>
<HStack gap="4" align="center">...</HStack>
<HGrid columns={{ xs: 1, md: 2 }} gap="4">...</HGrid>
```

### Backgrounds

```tsx
<Box background="surface-default">     {/* White */}
<Box background="surface-subtle">      {/* Light gray */}
<Box background="surface-action-subtle"> {/* Light blue */}
```

## Boundaries

### ✅ Always

- Use Aksel components and spacing tokens
- File-based routing via TanStack Router
- Type FHIR resources with shared types from `@utils/mapping/fhir`
- Mobile-first responsive design

### ⚠️ Ask First

- Adding new dependencies
- Custom CSS outside Aksel tokens
- Adding a state management library

### 🚫 Never

- Use Tailwind padding/margin utilities
- Use numeric spacing without `space-` prefix
- Import from `react-router-dom` (we use TanStack Router)
- Use Next.js patterns (getServerSideProps, API routes, App Router)
