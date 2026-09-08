"use client";

import "leaflet/dist/leaflet.css";
import "./sar-live-map.css";
import L from "leaflet";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { CircleMarker, MapContainer, Marker, Polyline, Popup, TileLayer, Tooltip, useMap } from "react-leaflet";
import { hasCoordinates, type LiveSquad } from "@/lib/live-squads";
import { getMapTileConfig, type LayerMode } from "@/lib/map-layers";
import { fetchTerrainElevationM } from "@/lib/elevation";
import { isCircleIcon, squadIconMapUrl } from "@/lib/squad-icons";

const DEFAULT_CENTER: L.LatLngExpression = [45.0703, 7.6869];

function formatDistanceMeters(meters: number): string {
  if (meters < 1000) {
    return `${Math.round(meters)} m`;
  }
  return `${(meters / 1000).toLocaleString("it-IT", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })} km`;
}

function pathLengthMeters(points: L.LatLng[]): number {
  let total = 0;
  for (let i = 1; i < points.length; i += 1) {
    total += points[i - 1]!.distanceTo(points[i]!);
  }
  return total;
}

function escapeHtml(value: string): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function formatLatLon(lat: number, lon: number): string {
  return `${lat.toFixed(6)} / ${lon.toFixed(6)}`;
}

function formatQuotaM(meters: number | null, loading: boolean): string {
  if (loading) {
    return "quota…";
  }
  if (meters == null) {
    return "quota n/d";
  }
  return `${meters} m`;
}

function formatCoordWithQuota(lat: number, lon: number, meters: number | null, loading: boolean): string {
  return `${formatLatLon(lat, lon)} · ${formatQuotaM(meters, loading)}`;
}

function coordPinIcon() {
  return L.divIcon({
    className: "sar-coord-divicon",
    html: `<div class="sar-coord-pin" aria-hidden="true"><span class="sar-coord-x"></span><span class="sar-coord-y"></span><span class="sar-coord-dot"></span></div>`,
    iconSize: [40, 40],
    iconAnchor: [20, 20],
  });
}

function chipWidth(code: string): number {
  return Math.max(56, Math.min(code.length * 8 + 18, 140));
}

function markerIcon(squad: LiveSquad, selected: boolean, alarming: boolean) {
  const color = alarming ? "#c62828" : squad.mapColor;
  const code = escapeHtml(squad.squadCode);
  const chipClass = alarming ? "sar-chip sar-chip-alarm" : "sar-chip";
  const w = chipWidth(squad.squadCode);

  if (isCircleIcon(squad.mapIconKey)) {
    const size = selected ? 22 : 16;
    const ring = selected ? "0 0 0 3px #e0be3a" : "0 1px 3px rgba(0,0,0,.45)";
    const h = size + 22;
    return L.divIcon({
      className: "sar-marker-divicon",
      html: `<div class="sar-pin" style="width:${w}px"><div class="sar-circle-pin" style="width:${size}px;height:${size}px;background:${escapeHtml(color)};box-shadow:${ring}"></div><div class="${chipClass}">${code}</div></div>`,
      iconSize: [w, h],
      iconAnchor: [w / 2, size / 2],
      popupAnchor: [0, -size / 2],
    });
  }

  const img = selected ? 40 : 32;
  const src = escapeHtml(squadIconMapUrl(squad.mapIconKey));
  const h = img + 26;
  return L.divIcon({
    className: "sar-marker-divicon",
    html: `<div class="sar-pin" style="width:${w}px"><img class="sar-png-img" src="${src}" width="${img}" height="${img}" alt="" /><span class="sar-png-dot" style="background:${escapeHtml(color)}"></span><div class="${chipClass}">${code}</div></div>`,
    iconSize: [w, h],
    iconAnchor: [w / 2, img / 2],
    popupAnchor: [0, -img / 2],
  });
}

function InvalidateOnResize() {
  const map = useMap();
  useEffect(() => {
    const invalidate = () => map.invalidateSize({ animate: false });
    invalidate();
    const t1 = window.setTimeout(invalidate, 80);
    const t2 = window.setTimeout(invalidate, 300);
    window.addEventListener("resize", invalidate);
    document.addEventListener("fullscreenchange", invalidate);
    const ro = new ResizeObserver(() => invalidate());
    const el = map.getContainer();
    ro.observe(el);
    if (el.parentElement) {
      ro.observe(el.parentElement);
    }
    return () => {
      window.clearTimeout(t1);
      window.clearTimeout(t2);
      window.removeEventListener("resize", invalidate);
      document.removeEventListener("fullscreenchange", invalidate);
      ro.disconnect();
    };
  }, [map]);
  return null;
}

function Recenter({ squad, nonce }: { squad: LiveSquad | null; nonce: number }) {
  const map = useMap();
  useEffect(() => {
    if (!squad || !hasCoordinates(squad)) {
      return;
    }
    map.flyTo([squad.lastLatitude!, squad.lastLongitude!], Math.max(map.getZoom(), 14), {
      duration: 0.45,
    });
    // Solo al click (nonce), non a ogni poll GPS.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [map, nonce]);
  return null;
}

function FocusLatLng({
  point,
  nonce,
}: {
  point: { lat: number; lng: number } | null;
  nonce: number;
}) {
  const map = useMap();
  useEffect(() => {
    if (!point) {
      return;
    }
    map.flyTo([point.lat, point.lng], Math.max(map.getZoom(), 16), { duration: 0.45 });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [map, nonce]);
  return null;
}

function FitWhenNeeded({ squads }: { squads: LiveSquad[] }) {
  const map = useMap();
  const fitted = useRef(false);
  useEffect(() => {
    const gps = squads.filter(hasCoordinates);
    if (fitted.current || gps.length === 0) {
      return;
    }
    const bounds = L.latLngBounds(gps.map((s) => [s.lastLatitude!, s.lastLongitude!] as [number, number]));
    map.fitBounds(bounds, { padding: [48, 48], maxZoom: 14 });
    fitted.current = true;
    window.setTimeout(() => map.invalidateSize({ animate: false }), 80);
  }, [map, squads]);
  return null;
}

function MapToolClicks({
  rulerOn,
  coordOn,
  onRulerAdd,
  onCoordSet,
  onCoordHover,
}: {
  rulerOn: boolean;
  coordOn: boolean;
  onRulerAdd: (latlng: L.LatLng) => void;
  onCoordSet: (latlng: L.LatLng) => void;
  onCoordHover: (latlng: L.LatLng | null) => void;
}) {
  const map = useMap();
  useEffect(() => {
    if (!rulerOn && !coordOn) {
      return;
    }
    const onClick = (e: L.LeafletMouseEvent) => {
      if (coordOn) {
        onCoordSet(e.latlng);
        return;
      }
      onRulerAdd(e.latlng);
    };
    const onMove = (e: L.LeafletMouseEvent) => {
      onCoordHover(e.latlng);
    };
    const onLeave = () => onCoordHover(null);
    map.on("click", onClick);
    if (coordOn) {
      map.on("mousemove", onMove);
      map.on("mouseout", onLeave);
    }
    map.doubleClickZoom.disable();
    map.getContainer().classList.add("sar-ruler-cursor");
    return () => {
      map.off("click", onClick);
      map.off("mousemove", onMove);
      map.off("mouseout", onLeave);
      map.doubleClickZoom.enable();
      map.getContainer().classList.remove("sar-ruler-cursor");
      onCoordHover(null);
    };
  }, [map, rulerOn, coordOn, onRulerAdd, onCoordSet, onCoordHover]);
  return null;
}

export default function SarLiveMap({
  squads,
  selectedSessionId,
  alarmingSessionIds,
  recenterNonce,
  onSelect,
  layerMode = "standard",
  focusPoint = null,
  focusNonce = 0,
}: {
  squads: LiveSquad[];
  selectedSessionId: string | null;
  alarmingSessionIds: ReadonlySet<string>;
  recenterNonce: number;
  onSelect: (squad: LiveSquad) => void;
  layerMode?: LayerMode;
  focusPoint?: { lat: number; lng: number } | null;
  focusNonce?: number;
}) {
  const selected = useMemo(
    () => squads.find((s) => s.sessionId === selectedSessionId) ?? null,
    [squads, selectedSessionId],
  );
  const tile = getMapTileConfig(layerMode);
  const [rulerOn, setRulerOn] = useState(false);
  const [rulerPoints, setRulerPoints] = useState<L.LatLng[]>([]);
  const [coordOn, setCoordOn] = useState(false);
  const [coordPoint, setCoordPoint] = useState<L.LatLng | null>(null);
  const [coordHover, setCoordHover] = useState<L.LatLng | null>(null);
  const [copied, setCopied] = useState(false);
  const [quotaM, setQuotaM] = useState<number | null>(null);
  const [quotaLoading, setQuotaLoading] = useState(false);

  const addRulerPoint = useCallback((latlng: L.LatLng) => {
    setRulerPoints((prev) => [...prev, latlng]);
  }, []);

  const setCoord = useCallback((latlng: L.LatLng) => {
    setCoordPoint(latlng);
  }, []);

  const hoverCoord = useCallback((latlng: L.LatLng | null) => {
    setCoordHover(latlng);
  }, []);

  useEffect(() => {
    if (!coordOn || !coordPoint) {
      setQuotaM(null);
      setQuotaLoading(false);
      return;
    }
    const lat = coordPoint.lat;
    const lon = coordPoint.lng;
    let cancelled = false;
    setQuotaLoading(true);
    const timer = window.setTimeout(() => {
      void fetchTerrainElevationM(lat, lon).then((m) => {
        if (cancelled) {
          return;
        }
        setQuotaM(m);
        setQuotaLoading(false);
      });
    }, 350);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [coordOn, coordPoint]);

  const rulerMeters = pathLengthMeters(rulerPoints);
  const shownCoord = coordPoint ?? (coordOn ? coordHover : null);

  function toggleRuler() {
    setRulerOn((on) => {
      if (on) {
        setRulerPoints([]);
        return false;
      }
      setCoordOn(false);
      setCoordPoint(null);
      return true;
    });
  }

  function toggleCoord() {
    setCoordOn((on) => {
      if (on) {
        setCoordPoint(null);
        setCoordHover(null);
        return false;
      }
      setRulerOn(false);
      return true;
    });
  }

  async function copyCoords() {
    if (!shownCoord) {
      return;
    }
    const text = shownCoord
      ? quotaM != null
        ? `${formatLatLon(shownCoord.lat, shownCoord.lng)}, quota ${quotaM} m`
        : formatLatLon(shownCoord.lat, shownCoord.lng)
      : "";
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    } catch {
      setCopied(false);
    }
  }

  const coordIcon = useMemo(() => coordPinIcon(), []);

  return (
    <div className="sar-map-shell">
      <div className="sar-ruler-bar">
        <button
          type="button"
          className={rulerOn ? "sar-ruler-btn sar-ruler-btn-on" : "sar-ruler-btn"}
          onClick={toggleRuler}
        >
          Righello
        </button>
        <button
          type="button"
          className={coordOn ? "sar-ruler-btn sar-ruler-btn-on" : "sar-ruler-btn"}
          onClick={toggleCoord}
        >
          Coordinate
        </button>
        {rulerOn ? (
          <>
            <span className="sar-ruler-distance">
              {rulerPoints.length < 2 ? "—" : formatDistanceMeters(rulerMeters)}
            </span>
            <button
              type="button"
              className="sar-ruler-btn"
              disabled={rulerPoints.length === 0}
              onClick={() => setRulerPoints((prev) => prev.slice(0, -1))}
            >
              Annulla punto
            </button>
            <button type="button" className="sar-ruler-btn" onClick={() => setRulerPoints([])}>
              Cancella
            </button>
            <span className="sar-ruler-hint">Clicca sulla mappa per misurare</span>
          </>
        ) : null}
        {coordOn ? (
          <>
            <span className="sar-ruler-distance">
              {shownCoord
                ? coordPoint
                  ? formatCoordWithQuota(shownCoord.lat, shownCoord.lng, quotaM, quotaLoading)
                  : formatLatLon(shownCoord.lat, shownCoord.lng)
                : "—"}
            </span>
            <button
              type="button"
              className="sar-ruler-btn"
              disabled={!shownCoord}
              onClick={() => void copyCoords()}
            >
              {copied ? "Copiato" : "Copia"}
            </button>
            <span className="sar-ruler-hint">
              {coordPoint ? "Trascina l’indicatore · quota terreno s.l.m." : "Tap sulla mappa, poi trascina"}
            </span>
          </>
        ) : null}
      </div>
      <MapContainer
        center={DEFAULT_CENTER}
        zoom={11}
        style={{ height: "100%", width: "100%" }}
        scrollWheelZoom
      >
        <TileLayer
          key={layerMode}
          attribution={tile.attribution}
          url={tile.url}
          maxZoom={tile.maxZoom}
          maxNativeZoom={tile.maxNativeZoom}
        />
        <InvalidateOnResize />
        <FitWhenNeeded squads={squads} />
        <Recenter squad={selected} nonce={recenterNonce} />
        <FocusLatLng point={focusPoint} nonce={focusNonce} />
        <MapToolClicks
          rulerOn={rulerOn}
          coordOn={coordOn}
          onRulerAdd={addRulerPoint}
          onCoordSet={setCoord}
          onCoordHover={hoverCoord}
        />
        {rulerPoints.length >= 2 ? (
          <>
            <Polyline
              positions={rulerPoints}
              pathOptions={{ color: "#ffffff", weight: 5, opacity: 0.95, lineCap: "round", lineJoin: "round" }}
            />
            <Polyline
              positions={rulerPoints}
              pathOptions={{ color: "#ff1a1a", weight: 2, opacity: 1, lineCap: "round", lineJoin: "round" }}
            />
          </>
        ) : null}
        {rulerPoints.map((p, i) => (
          <CircleMarker
            key={`${p.lat.toFixed(6)}-${p.lng.toFixed(6)}-${i}`}
            center={p}
            radius={5}
            pathOptions={{ color: "#fff", weight: 2, fillColor: "#ff1a1a", fillOpacity: 1 }}
          >
            {i === rulerPoints.length - 1 && rulerPoints.length >= 2 ? (
              <Tooltip permanent direction="top">
                {formatDistanceMeters(rulerMeters)}
              </Tooltip>
            ) : null}
          </CircleMarker>
        ))}
        {coordOn && coordPoint ? (
          <Marker
            position={coordPoint}
            draggable
            icon={coordIcon}
            zIndexOffset={900}
            eventHandlers={{
              drag: (e) => setCoordPoint(e.target.getLatLng()),
              dragend: (e) => setCoordPoint(e.target.getLatLng()),
            }}
          >
            <Tooltip permanent direction="top">
              {formatCoordWithQuota(coordPoint.lat, coordPoint.lng, quotaM, quotaLoading)}
            </Tooltip>
          </Marker>
        ) : null}
        {focusPoint ? (
          <CircleMarker
            center={[focusPoint.lat, focusPoint.lng]}
            radius={9}
            pathOptions={{ color: "#fff", weight: 2, fillColor: "#e0be3a", fillOpacity: 0.95 }}
          >
            <Tooltip permanent direction="top">
              FOTO
            </Tooltip>
          </CircleMarker>
        ) : null}
        {squads.filter(hasCoordinates).map((s) => {
          const alarming = alarmingSessionIds.has(s.sessionId);
          const isSelected = s.sessionId === selectedSessionId;
          return (
            <Marker
              key={s.sessionId}
              position={[s.lastLatitude!, s.lastLongitude!]}
              icon={markerIcon(s, isSelected, alarming)}
              zIndexOffset={isSelected ? 600 : alarming ? 400 : 0}
              eventHandlers={{
                click: (e) => {
                  L.DomEvent.stop(e);
                  if (coordOn) {
                    setCoord(L.latLng(s.lastLatitude!, s.lastLongitude!));
                  } else if (rulerOn) {
                    addRulerPoint(L.latLng(s.lastLatitude!, s.lastLongitude!));
                  }
                  onSelect(s);
                },
              }}
            >
              <Popup>
                <strong>{s.squadCode}</strong>
                <br />
                {s.squadName}
                {s.lastAccuracy != null ? (
                  <>
                    <br />± {Math.round(s.lastAccuracy)} m
                  </>
                ) : null}
              </Popup>
            </Marker>
          );
        })}
      </MapContainer>
    </div>
  );
}
