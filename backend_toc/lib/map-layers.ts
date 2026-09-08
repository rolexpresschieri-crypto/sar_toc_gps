export type LayerMode = "standard" | "topo" | "orthophoto";

export const layerOptions: { value: LayerMode; label: string }[] = [
  { value: "standard", label: "OpenStreetMap" },
  { value: "topo", label: "Topografica" },
  { value: "orthophoto", label: "Ortofoto" },
];

export function getMapTileConfig(layerMode: LayerMode): {
  url: string;
  attribution: string;
  maxZoom: number;
  maxNativeZoom: number;
} {
  if (layerMode === "orthophoto") {
    return {
      url: "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
      attribution: "&copy; Esri, Maxar, Earthstar Geographics",
      maxZoom: 20,
      maxNativeZoom: 19,
    };
  }
  if (layerMode === "topo") {
    return {
      url: "https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png",
      attribution: "&copy; OpenStreetMap, SRTM · OpenTopoMap (CC-BY-SA)",
      maxZoom: 17,
      maxNativeZoom: 17,
    };
  }
  return {
    url: "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
    maxZoom: 19,
    maxNativeZoom: 19,
  };
}

export function parseLayerMode(raw: string | null | undefined): LayerMode | null {
  if (raw === "standard" || raw === "topo" || raw === "orthophoto") {
    return raw;
  }
  return null;
}
