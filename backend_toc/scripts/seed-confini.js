const fs = require("fs");
const path = require("path");
const { createClient } = require("@supabase/supabase-js");

function loadEnv(file) {
  const out = {};
  for (const line of fs.readFileSync(file, "utf8").split(/\r?\n/)) {
    const m = line.match(/^([A-Z0-9_]+)=(.*)$/);
    if (m) {
      out[m[1]] = m[2].replace(/^["']|["']$/g, "").trim();
    }
  }
  return out;
}

async function main() {
  const env = loadEnv(path.join(__dirname, "..", ".env.local"));
  const url = env.NEXT_PUBLIC_SUPABASE_URL;
  const key = env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
  if (!url || !key) {
    throw new Error("Manca NEXT_PUBLIC_SUPABASE_URL / ANON_KEY");
  }
  const supabase = createClient(url, key);
  const created = await fetch(`${url}/storage/v1/bucket`, {
    method: "POST",
    headers: {
      apikey: key,
      Authorization: `Bearer ${key}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      id: "comune-boundaries",
      name: "comune-boundaries",
      public: false,
      file_size_limit: 4194304,
    }),
  });
  const createdBody = await created.text();
  console.log("create-bucket", created.status, createdBody.slice(0, 240));
  const dir = path.join(__dirname, "..", "public", "map", "confini");
  const names = {
    "claviere.geojson": "Claviere",
    "cesana-torinese.geojson": "Cesana Torinese",
    "sauze-di-cesana.geojson": "Sauze di Cesana",
    "sestriere.geojson": "Sestriere",
    "sauze-d-oulx.geojson": "Sauze d'Oulx",
    "oulx.geojson": "Oulx",
    "pragelato.geojson": "Pragelato",
  };
  let ok = 0;
  for (const [file, name] of Object.entries(names)) {
    const geo = JSON.parse(fs.readFileSync(path.join(dir, file), "utf8"));
    if (geo.features?.[0]) {
      geo.features[0].properties = { ...(geo.features[0].properties || {}), name };
    }
    const body = Buffer.from(JSON.stringify(geo));
    const { error } = await supabase.storage.from("comune-boundaries").upload(file, body, {
      contentType: "application/geo+json",
      upsert: true,
    });
    if (error) {
      console.error(file, error.message);
      process.exitCode = 1;
    } else {
      ok += 1;
      console.log("ok", name);
    }
  }
  console.log("uploaded", ok);
}

main().catch((err) => {
  console.error(err.message || err);
  process.exit(1);
});
