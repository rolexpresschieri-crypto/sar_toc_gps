-- Admin TOC per ente: login come l'app (ente + codice + password).
-- Eseguire in Supabase → SQL Editor DOPO sql/organizations.sql.
--
-- Enti (organizations): li crei/sospendi tu in Table Editor
--   is_enabled = false → login app e TOC bloccati per quell'ente.
--
-- Seed login tuo: NVANSMI / ADMIN_RR / 123456

alter table toc_admins
  add column if not exists organization_id uuid references organizations(id) on delete restrict;

alter table toc_admins drop constraint if exists toc_admins_admin_code_key;

create unique index if not exists toc_admins_organization_admin_code_uidx
  on toc_admins (organization_id, admin_code);

comment on index toc_admins_organization_admin_code_uidx is
  'Un admin_code è unico dentro l''ente, non in tutto il DB.';

create index if not exists toc_admins_organization_idx on toc_admins (organization_id);

update toc_admins
set is_enabled = false
where admin_code in ('TOC01', 'GOLF_TORINO');

-- Collega (o crea) ADMIN_RR a NVANSMI. Se la riga c'è già senza ente, la aggiorna.
update toc_admins t
set
  organization_id = o.id,
  admin_name = 'Roberto Ronco',
  password_hash = '123456',
  role = 'admin',
  is_enabled = true
from organizations o
where o.org_code = 'NVANSMI'
  and t.admin_code = 'ADMIN_RR';

insert into toc_admins (
  admin_code, admin_name, password_hash, role, is_enabled, organization_id
)
select 'ADMIN_RR', 'Roberto Ronco', '123456', 'admin', true, o.id
from organizations o
where o.org_code = 'NVANSMI'
  and not exists (
    select 1 from toc_admins a where a.admin_code = 'ADMIN_RR'
  );
