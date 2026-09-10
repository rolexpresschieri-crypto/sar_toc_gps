-- Catalogo TRK cartella TRK_NOVALESA (opzionale: l'app elenca anche lo Storage da sola).
-- Storage: mission-gps / NVANSMI / TRK_NOVALESA / <file>.trk

insert into mission_gps_files (organization_id, kind, file_name, storage_path)
select o.id, 'trk', v.file_name, v.storage_path
from organizations o
cross join (
  values
    ('Zona S1_28K_06.trk', 'NVANSMI/TRK_NOVALESA/Zona S1_28K_06.trk'),
    ('Zona S1_30K_01.trk', 'NVANSMI/TRK_NOVALESA/Zona S1_30K_01.trk'),
    ('Zona S1_30K_02.trk', 'NVANSMI/TRK_NOVALESA/Zona S1_30K_02.trk'),
    ('Zona S1_50K_03.trk', 'NVANSMI/TRK_NOVALESA/Zona S1_50K_03.trk'),
    ('Zona S1_63K_07.trk', 'NVANSMI/TRK_NOVALESA/Zona S1_63K_07.trk'),
    ('Zona S1_88K_04.trk', 'NVANSMI/TRK_NOVALESA/Zona S1_88K_04.trk'),
    ('Zona S1_93K_05.trk', 'NVANSMI/TRK_NOVALESA/Zona S1_93K_05.trk')
) as v(file_name, storage_path)
where o.org_code = 'NVANSMI'
on conflict (organization_id, kind, file_name) do update set
  storage_path = excluded.storage_path,
  is_enabled = true;
