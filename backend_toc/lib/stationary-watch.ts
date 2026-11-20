"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { hasCoordinates, type LiveSquad } from "@/lib/live-squads";

/** Test: 20 s. Produzione prevista: 120_000 (2 min). */
export const STATIONARY_THRESHOLD_MS = 20_000;
/** Durata allarme sonoro (bip-bip-bip). */
export const STATIONARY_SOUND_MS = 15_000;
/** Spostamento oltre questa soglia = di nuovo in moto. */
export const STATIONARY_MOVE_M = 10;

function haversineM(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const r = 6_371_000;
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

type TrackState = {
  lat: number;
  lon: number;
  settledAt: number;
};

let audioCtx: AudioContext | null = null;
let masterGain: GainNode | null = null;
let soundGeneration = 0;

function getAudioCtx(): AudioContext | null {
  if (typeof window === "undefined") return null;
  const AC =
    window.AudioContext ||
    (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (!AC) return null;
  if (!audioCtx) {
    audioCtx = new AC();
    masterGain = audioCtx.createGain();
    masterGain.gain.value = 1;
    masterGain.connect(audioCtx.destination);
  }
  return audioCtx;
}

/** Interrompe i bip in corso. */
export function stopStationaryBips(): void {
  soundGeneration += 1;
  if (masterGain && audioCtx) {
    const now = audioCtx.currentTime;
    masterGain.gain.cancelScheduledValues(now);
    masterGain.gain.setValueAtTime(0.0001, now);
  }
}

/** Serie di bip per ~durationMs (default 15 s). */
export function playStationaryBips(durationMs = STATIONARY_SOUND_MS): void {
  const ctx = getAudioCtx();
  if (!ctx || !masterGain) return;
  void ctx.resume();
  soundGeneration += 1;
  const gen = soundGeneration;
  masterGain.gain.cancelScheduledValues(ctx.currentTime);
  masterGain.gain.setValueAtTime(1, ctx.currentTime);
  const start = ctx.currentTime;
  const every = 0.42;
  let t = 0;
  while (t * 1000 < durationMs) {
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = "square";
    osc.frequency.value = 920;
    const t0 = start + t;
    gain.gain.setValueAtTime(0.0001, t0);
    gain.gain.exponentialRampToValueAtTime(0.22, t0 + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.0001, t0 + 0.11);
    osc.connect(gain);
    gain.connect(masterGain);
    osc.start(t0);
    osc.stop(t0 + 0.13);
    osc.onended = () => {
      if (gen !== soundGeneration) {
        try {
          osc.disconnect();
          gain.disconnect();
        } catch {
          /* already disconnected */
        }
      }
    };
    t += every;
  }
}

export type StationaryWatch = {
  stationaryIds: ReadonlySet<string>;
  /** Sessioni con allarme fermo escluso (fermo atteso). */
  excludedIds: ReadonlySet<string>;
  /** Reset: toglie cerchi/campana/suono e riavvia il timer di fermo. */
  dismissStationary: (sessionId: string) => void;
  /**
   * Toggle esclusione: ON = ferma allarme (rosso),
   * OFF = riparte il timer (verde).
   */
  toggleStationaryExclude: (sessionId: string) => void;
};

/**
 * Restituisce gli sessionId online fermi da almeno STATIONARY_THRESHOLD_MS.
 * All’ingresso in allarme parte il bip (una volta per episodio di fermo).
 */
export function useStationaryWatch(squads: LiveSquad[]): StationaryWatch {
  const [ids, setIds] = useState<ReadonlySet<string>>(() => new Set());
  const [excludedIds, setExcludedIds] = useState<ReadonlySet<string>>(() => new Set());
  const tracksRef = useRef<Map<string, TrackState>>(new Map());
  const soundedRef = useRef<Set<string>>(new Set());
  const excludedRef = useRef<Set<string>>(new Set());

  const clearAlarmUi = useCallback((sessionId: string) => {
    soundedRef.current.delete(sessionId);
    stopStationaryBips();
    setIds((cur) => {
      if (!cur.has(sessionId)) return cur;
      const next = new Set(cur);
      next.delete(sessionId);
      return next;
    });
  }, []);

  const dismissStationary = useCallback(
    (sessionId: string) => {
      if (excludedRef.current.has(sessionId)) return;
      const now = Date.now();
      const prev = tracksRef.current.get(sessionId);
      if (prev) {
        tracksRef.current.set(sessionId, { ...prev, settledAt: now });
      }
      clearAlarmUi(sessionId);
    },
    [clearAlarmUi],
  );

  const toggleStationaryExclude = useCallback(
    (sessionId: string) => {
      const now = Date.now();
      if (excludedRef.current.has(sessionId)) {
        // Riprendi monitoraggio: timer da zero
        excludedRef.current.delete(sessionId);
        const prev = tracksRef.current.get(sessionId);
        if (prev) {
          tracksRef.current.set(sessionId, { ...prev, settledAt: now });
        }
        soundedRef.current.delete(sessionId);
        setExcludedIds(new Set(excludedRef.current));
        return;
      }
      // Escludi: ferma allarme e non riparte finché non si riattiva
      excludedRef.current.add(sessionId);
      clearAlarmUi(sessionId);
      setExcludedIds(new Set(excludedRef.current));
    },
    [clearAlarmUi],
  );

  useEffect(() => {
    const recompute = () => {
      const now = Date.now();
      const tracks = tracksRef.current;
      const liveIds = new Set(squads.map((s) => s.sessionId));

      for (const key of [...tracks.keys()]) {
        if (!liveIds.has(key)) {
          tracks.delete(key);
          soundedRef.current.delete(key);
          if (excludedRef.current.delete(key)) {
            setExcludedIds(new Set(excludedRef.current));
          }
        }
      }

      const next = new Set<string>();
      for (const s of squads) {
        if (!s.isOnline || !hasCoordinates(s)) {
          tracks.delete(s.sessionId);
          soundedRef.current.delete(s.sessionId);
          if (excludedRef.current.delete(s.sessionId)) {
            setExcludedIds(new Set(excludedRef.current));
          }
          continue;
        }
        const lat = s.lastLatitude!;
        const lon = s.lastLongitude!;
        const prev = tracks.get(s.sessionId);
        if (!prev) {
          tracks.set(s.sessionId, { lat, lon, settledAt: now });
          continue;
        }
        const moved = haversineM(prev.lat, prev.lon, lat, lon);
        if (moved >= STATIONARY_MOVE_M) {
          tracks.set(s.sessionId, { lat, lon, settledAt: now });
          soundedRef.current.delete(s.sessionId);
          continue;
        }
        // stessa posizione (o jitter): aggiorna coord ma non il timer
        tracks.set(s.sessionId, { lat, lon, settledAt: prev.settledAt });
        if (excludedRef.current.has(s.sessionId)) {
          continue;
        }
        if (now - prev.settledAt >= STATIONARY_THRESHOLD_MS) {
          next.add(s.sessionId);
          if (!soundedRef.current.has(s.sessionId)) {
            soundedRef.current.add(s.sessionId);
            playStationaryBips(STATIONARY_SOUND_MS);
          }
        }
      }

      setIds((prev) => {
        if (prev.size === next.size && [...next].every((id) => prev.has(id))) {
          return prev;
        }
        return next;
      });
    };

    recompute();
    const tick = window.setInterval(recompute, 1000);
    return () => window.clearInterval(tick);
  }, [squads]);

  return {
    stationaryIds: ids,
    excludedIds,
    dismissStationary,
    toggleStationaryExclude,
  };
}
