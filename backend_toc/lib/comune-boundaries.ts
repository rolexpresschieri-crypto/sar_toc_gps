export type ComuneBoundary = {
  id: string;
  name: string;
  file: string;
};

export const COMUNE_BOUNDARIES: ComuneBoundary[] = [
  { id: "claviere", name: "Claviere", file: "/map/confini/claviere.geojson" },
  { id: "cesana-torinese", name: "Cesana Torinese", file: "/map/confini/cesana-torinese.geojson" },
  { id: "sauze-di-cesana", name: "Sauze di Cesana", file: "/map/confini/sauze-di-cesana.geojson" },
  { id: "sestriere", name: "Sestriere", file: "/map/confini/sestriere.geojson" },
  { id: "sauze-d-oulx", name: "Sauze d'Oulx", file: "/map/confini/sauze-d-oulx.geojson" },
  { id: "oulx", name: "Oulx", file: "/map/confini/oulx.geojson" },
  { id: "pragelato", name: "Pragelato", file: "/map/confini/pragelato.geojson" },
];
