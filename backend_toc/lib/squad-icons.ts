export type SquadIconKey = "circle" | "ambulanza" | "waypoint_toc";

export type SquadIconOption = {
  key: SquadIconKey;
  label: string;
  mapUrl: string | null;
};

export const SQUAD_ICON_OPTIONS: SquadIconOption[] = [
  { key: "circle", label: "Cerchio", mapUrl: null },
  { key: "ambulanza", label: "Ambulanza", mapUrl: "/map/squad/ambulanza.png" },
  { key: "waypoint_toc", label: "T.O.C.", mapUrl: "/map/squad/waypoint_toc.png" },
];

export const DEFAULT_SQUAD_ICON_KEY: SquadIconKey = "circle";

const PNG_URL_BY_KEY: Record<Exclude<SquadIconKey, "circle">, string> = {
  ambulanza: "/map/squad/ambulanza.png",
  waypoint_toc: "/map/squad/waypoint_toc.png",
};

/** Valori salvati in DB con il catalogo precedente. */
const SQUAD_ICON_ALIASES: Record<string, SquadIconKey> = {
  squadre_a_piedi: "circle",
  vigili_fuoco: "circle",
  forze_ordine: "circle",
  medico: "circle",
  logo_ansmi: "circle",
  nv_ansmi: "circle",
  ansmi: "circle",
  logo_nv_ansmi: "circle",
  coordinatore_cri: "circle",
  fig: "circle",
};

export function normalizeSquadIconKey(raw: string | null | undefined): SquadIconKey {
  const k = (raw ?? "").trim();
  if (k === "circle" || k === "ambulanza" || k === "waypoint_toc") {
    return k;
  }
  if (k in SQUAD_ICON_ALIASES) {
    return SQUAD_ICON_ALIASES[k]!;
  }
  return DEFAULT_SQUAD_ICON_KEY;
}

export function isCircleIcon(iconKey: string | null | undefined): boolean {
  return normalizeSquadIconKey(iconKey) === "circle";
}

export function squadIconMapUrl(iconKey: string | null | undefined): string {
  const k = normalizeSquadIconKey(iconKey);
  if (k === "circle") {
    return "";
  }
  return PNG_URL_BY_KEY[k];
}
