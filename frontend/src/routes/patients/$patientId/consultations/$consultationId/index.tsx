import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute(
  "/patients/$patientId/consultations/$consultationId/",
)({
  component: () => null,
});
