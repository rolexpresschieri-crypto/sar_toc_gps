const ACKED_PHOTOS_KEY = "toc_sar_acked_photo_ids";

export function readAckedPhotoIds(): Set<string> {
  if (typeof window === "undefined") {
    return new Set();
  }
  try {
    const raw = window.localStorage.getItem(ACKED_PHOTOS_KEY);
    const parsed = raw ? (JSON.parse(raw) as unknown) : [];
    if (!Array.isArray(parsed)) {
      return new Set();
    }
    return new Set(parsed.filter((v): v is string => typeof v === "string"));
  } catch {
    return new Set();
  }
}

export function addAckedPhotoId(id: string): void {
  const next = readAckedPhotoIds();
  next.add(id);
  window.localStorage.setItem(ACKED_PHOTOS_KEY, JSON.stringify([...next]));
}
