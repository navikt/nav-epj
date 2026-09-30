import { isLocalhost } from "../utils/env";

export function canCreatePatients() {
  try {
    return isLocalhost();
  } catch {
    return false;
  }
}
