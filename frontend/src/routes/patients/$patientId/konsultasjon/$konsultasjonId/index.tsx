import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute(
  "/patients/$patientId/konsultasjon/$konsultasjonId/",
)({
  component: () => null,
});
