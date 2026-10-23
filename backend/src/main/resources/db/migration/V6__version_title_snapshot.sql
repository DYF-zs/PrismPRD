ALTER TABLE prd_version ADD COLUMN title_snapshot varchar(255);
-- Legacy versions have no historical title; freeze the available title once at migration time.
UPDATE prd_version v SET title_snapshot = d.title FROM prd_document d WHERE d.id = v.prd_id;
ALTER TABLE prd_version ALTER COLUMN title_snapshot SET NOT NULL;
