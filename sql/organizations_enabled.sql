-- Flag attivo/sospeso sugli enti (edit manuale in Table Editor da admin generale).
-- Eseguire dopo sql/organizations.sql.

alter table organizations
  add column if not exists is_enabled boolean not null default true;

comment on column organizations.is_enabled is
  'true = login consentito. false = ente in anagrafica ma login bloccato. Default true in insert.';
