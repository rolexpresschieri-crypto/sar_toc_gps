import { parseLayerMode, type LayerMode } from "@/lib/map-layers";

export const MAP_LAYER_STORAGE_KEY = "toc_sar_map_layer";

export function readStoredLayerMode(): LayerMode {
  if (typeof window === "undefined") {
    return "standard";
  }
  return parseLayerMode(window.localStorage.getItem(MAP_LAYER_STORAGE_KEY)) ?? "standard";
}

export function writeStoredLayerMode(mode: LayerMode): void {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.setItem(MAP_LAYER_STORAGE_KEY, mode);
}
