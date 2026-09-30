import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/patients/$patientId/")({
  component: () => null,
});
