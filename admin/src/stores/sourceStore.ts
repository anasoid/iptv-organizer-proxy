import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface SourceStore {
  selectedSourceId: number | null;
  setSelectedSourceId: (sourceId: number | null) => void;
}

export const useSourceStore = create<SourceStore>()(
  persist(
    (set) => ({
      selectedSourceId: null,
      setSelectedSourceId: (sourceId: number | null) => set({ selectedSourceId: sourceId }),
    }),
    {
      name: 'source-store', // localStorage key
    }
  )
);
