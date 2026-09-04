-- Login a tre campi: unique operatore per ente, non più globale.
-- Codice ente sempre MAIUSCOLO. Eseguire dopo sql/organizations.sql.
-- Così un secondo ente può avere un proprio LUPO / OP001.

alter table squads drop constraint if exists squads_squad_code_key;

create unique index if not exists squads_organization_squad_code_uidx
  on squads (organization_id, squad_code);

comment on index squads_organization_squad_code_uidx is
  'Un squad_code è unico dentro l''ente, non in tutto il DB.';
