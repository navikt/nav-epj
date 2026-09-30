import { create } from "zustand";

type ModalState = {
  openCount: number;
  enter: () => void;
  leave: () => void;
};

export const useModalStore = create<ModalState>((set) => ({
  openCount: 0,
  enter: () => set((s) => ({ openCount: s.openCount + 1 })),
  leave: () => set((s) => ({ openCount: Math.max(0, s.openCount - 1) })),
}));

export function isModalOpen() {
  return useModalStore.getState().openCount > 0;
}
