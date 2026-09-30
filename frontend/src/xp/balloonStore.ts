import { create } from "zustand";
import type { IconName } from "./XpIcon";

export type Balloon = {
  id: number;
  title: string;
  body: string;
  icon?: IconName;
};

type BalloonState = {
  balloon: Balloon | null;
  show: (balloon: Omit<Balloon, "id">) => void;
  dismiss: (id: number) => void;
};

let nextId = 1;

export const useBalloonStore = create<BalloonState>((set) => ({
  balloon: null,
  show: (balloon) => set({ balloon: { ...balloon, id: nextId++ } }),
  dismiss: (id) =>
    set((s) => (s.balloon?.id === id ? { balloon: null } : s)),
}));
