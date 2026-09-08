import { readStoredLayerMode } from "@/lib/map-layer-storage";
import type { LayerMode } from "@/lib/map-layers";

const MAP_DISPLAY_WINDOW_NAME = "tocSarMapDisplay";

function getStoredMapDisplayWindow(): Window | null {
  if (typeof window === "undefined") {
    return null;
  }
  const win = window.__tocSarMapDisplayWin;
  if (win && !win.closed) {
    return win;
  }
  window.__tocSarMapDisplayWin = null;
  return null;
}

/** Apre la mappa in una nuova finestra da trascinare sul secondo monitor. */
export function openExternalMapWindow(layerMode?: LayerMode): Window | null {
  if (typeof window === "undefined") {
    return null;
  }

  const existing = getStoredMapDisplayWindow();
  if (existing) {
    existing.focus();
    return existing;
  }

  const layer = layerMode ?? readStoredLayerMode();
  const url = `${window.location.origin}/map-fullscreen?display=1&layer=${layer}`;
  const w = Math.min(window.screen.availWidth, 2560);
  const h = Math.min(window.screen.availHeight, 1440);
  const features = [
    `width=${w}`,
    `height=${h}`,
    "left=80",
    "top=40",
    "menubar=no",
    "toolbar=no",
    "location=no",
    "status=no",
    "resizable=yes",
    "scrollbars=no",
  ].join(",");

  const win = window.open(url, MAP_DISPLAY_WINDOW_NAME, features);
  if (win) {
    window.__tocSarMapDisplayWin = win;
    win.focus();
  }
  return win;
}

declare global {
  interface Window {
    __tocSarMapDisplayWin?: Window | null;
  }
}
