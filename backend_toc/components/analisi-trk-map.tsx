"use client";

import { useEffect, useMemo, useRef } from "react";
import { MapContainer, Polygon, Polyline, TileLayer, useMap } from "react-leaflet";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import type { TrkPoint } from "@/lib/trk-analysis";
import { trackLatLngs } from "@/lib/trk-analysis";

const AREA = "#2e7d32";
const CANE = "#e64a19";
const COND = "#1e88e5";

export type AnalisiMapHandle = {
  /** Zoom stretto sulle tracce e attesa tile (per PDF). */
  prepareForCapture: () => Promise<void>;
};

function buildBounds(
  area: Array<[number, number]>,
  cane: Array<[number, number]>,
  conduttore: Array<[number, number]>,
): L.LatLngBounds | null {
  const all = [...area, ...cane, ...conduttore];
  if (all.length < 2) return null;
  const b = L.latLngBounds(all.map(([lat, lon]) => L.latLng(lat, lon)));
  return b.isValid() ? b : null;
}

function MapController({
  area,
  cane,
  conduttore,
  onReady,
}: {
  area: Array<[number, number]>;
  cane: Array<[number, number]>;
  conduttore: Array<[number, number]>;
  onReady?: (handle: AnalisiMapHandle) => void;
}) {
  const map = useMap();
  const fitGen = useRef(0);

  const fitTight = () => {
    const bounds = buildBounds(area, cane, conduttore);
    if (!bounds) return;
    map.invalidateSize(true);
    // pad geometrico + padding pixel: tracce al centro, non in un angolo
    map.fitBounds(bounds.pad(0.18), {
      paddingTopLeft: [48, 48],
      paddingBottomRight: [48, 48],
      maxZoom: 17,
      animate: false,
    });
  };

  useEffect(() => {
    const gen = ++fitGen.current;
    const run = () => {
      if (fitGen.current !== gen) return;
      fitTight();
    };
    run();
    const timers = [80, 250, 600, 1200].map((ms) => window.setTimeout(run, ms));
    const el = map.getContainer();
    const ro = new ResizeObserver(() => {
      if (fitGen.current !== gen) return;
      map.invalidateSize(true);
      window.setTimeout(run, 30);
    });
    ro.observe(el);
    return () => {
      timers.forEach((id) => window.clearTimeout(id));
      ro.disconnect();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- fit when track geometry changes
  }, [map, area, cane, conduttore]);

  useEffect(() => {
    if (!onReady) return;
    onReady({
      prepareForCapture: () =>
        new Promise((resolve) => {
          let finished = false;
          const container = map.getContainer();
          const ctrl = container.querySelector(".leaflet-control-container") as HTMLElement | null;
          const prev = ctrl?.style.display ?? "";
          if (ctrl) ctrl.style.display = "none";

          const finish = () => {
            if (finished) return;
            finished = true;
            window.setTimeout(() => {
              if (ctrl) ctrl.style.display = prev;
              resolve();
            }, 1400);
          };

          map.invalidateSize(true);
          fitTight();
          window.setTimeout(() => {
            fitTight();
            map.once("moveend", () => {
              window.setTimeout(() => {
                fitTight();
                window.setTimeout(finish, 400);
              }, 120);
            });
            window.setTimeout(finish, 2400);
          }, 150);
        }),
    });
  }, [map, onReady, area, cane, conduttore]);

  return null;
}

export default function AnalisiTrkMap({
  areaPoints,
  canePoints,
  conduttorePoints,
  onMapReady,
}: {
  areaPoints: TrkPoint[];
  canePoints: TrkPoint[];
  conduttorePoints: TrkPoint[];
  onMapReady?: (handle: AnalisiMapHandle) => void;
}) {
  const area = useMemo(() => trackLatLngs(areaPoints), [areaPoints]);
  const cane = useMemo(() => trackLatLngs(canePoints), [canePoints]);
  const conduttore = useMemo(() => trackLatLngs(conduttorePoints), [conduttorePoints]);
  const center = cane[0] ?? conduttore[0] ?? area[0] ?? ([45.0, 7.0] as [number, number]);

  return (
    <MapContainer
      center={center}
      zoom={16}
      scrollWheelZoom
      preferCanvas
      style={{ width: "100%", height: "100%" }}
    >
      <TileLayer
        attribution='Map data: &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors, <a href="http://viewfinderpanoramas.org">SRTM</a> | Map style: &copy; <a href="https://opentopomap.org">OpenTopoMap</a> (<a href="https://creativecommons.org/licenses/by-sa/3.0/">CC-BY-SA</a>)'
        url="https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png"
        maxZoom={17}
        crossOrigin={true}
      />
      {area.length >= 2 ? (
        <Polygon
          positions={area}
          pathOptions={{
            color: AREA,
            weight: 4,
            opacity: 0.95,
            fillColor: AREA,
            fillOpacity: 0.22,
          }}
        />
      ) : null}
      {conduttore.length >= 2 ? (
        <Polyline positions={conduttore} pathOptions={{ color: COND, weight: 4, opacity: 0.9 }} />
      ) : null}
      {cane.length >= 2 ? (
        <Polyline positions={cane} pathOptions={{ color: CANE, weight: 4, opacity: 0.95 }} />
      ) : null}
      <MapController area={area} cane={cane} conduttore={conduttore} onReady={onMapReady} />
    </MapContainer>
  );
}
