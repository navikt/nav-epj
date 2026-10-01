import { createFileRoute } from "@tanstack/react-router";
import { StartTab } from "../xp/StartTab";

export const Route = createFileRoute("/")({
  component: StartTab,
});
