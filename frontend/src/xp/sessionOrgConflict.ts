type Listener = () => void;

const listeners = new Set<Listener>();

export function onSessionOrgConflict(listener: Listener) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function notifySessionOrgConflict() {
  listeners.forEach((listener) => listener());
}
