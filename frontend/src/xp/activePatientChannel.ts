const CHANNEL_NAME = "nav-epj:active-patient";

let sender: BroadcastChannel | null = null;

export function broadcastActivePatient(patientId: string) {
  if (typeof BroadcastChannel === "undefined") return;
  sender ??= new BroadcastChannel(CHANNEL_NAME);
  sender.postMessage(patientId);
}

export function listenForActivePatient(onChange: () => void) {
  if (typeof BroadcastChannel === "undefined") return () => {};
  const receiver = new BroadcastChannel(CHANNEL_NAME);
  receiver.onmessage = onChange;
  return () => receiver.close();
}
