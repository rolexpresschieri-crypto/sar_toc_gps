-- Seed operatori demo SAR (tabella DB: squads = anagrafica operatori)
-- Eseguire dopo schema_v1.sql. Adatta codici/password/icone a piacere.

insert into squads (squad_code, squad_name, password_hash, map_color, map_icon_key, is_enabled)
values
  ('LUPO', 'Roberto Ronco', '1234', '#079B42', 'squadre_a_piedi', true),
  ('OP001', 'Operatore Alpha', '1234', '#079B42', 'squadre_a_piedi', true),
  ('OP002', 'Operatore Bravo', '1234', '#E53935', 'ambulanza', true)
on conflict (squad_code) do update set
  squad_name = excluded.squad_name,
  password_hash = excluded.password_hash,
  map_color = excluded.map_color,
  map_icon_key = excluded.map_icon_key,
  is_enabled = excluded.is_enabled;

-- Assicura un evento attivo
insert into events (title, description, is_active)
select 'Evento operativo TOC SAR', 'Evento demo', true
where not exists (select 1 from events where is_active = true);
