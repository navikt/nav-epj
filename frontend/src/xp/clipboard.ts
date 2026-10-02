import { useBalloonStore } from "./balloonStore";
import { copy } from "./copy";

export async function copyText(text: string, title: string) {
  try {
    await navigator.clipboard.writeText(text);
  } catch {
    return;
  }
  useBalloonStore.getState().show({ title, body: copy["common.copied.body"] });
}
