export function focusTab(tabId: string) {
  setTimeout(() => document.getElementById(`tab-${tabId}`)?.focus(), 0);
}
