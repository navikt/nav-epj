export function createSession() {
  let current = 0;
  return {
    next() {
      current += 1;
      return current;
    },
    capture() {
      const captured = current;
      return () => captured === current;
    },
  };
}
