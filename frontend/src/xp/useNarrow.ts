import { useMediaQuery } from "./useMediaQuery";

export function useNarrow() {
  return useMediaQuery("(max-width: 1023px)");
}
