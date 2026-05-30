-- V35: add optional section_id to ptm_slots so a slot can be tied to a class/section
-- When section_id is set, the backend broadcasts a PTM announcement to all parents in that class.

ALTER TABLE ptm_slots
    ADD COLUMN section_id UUID REFERENCES sections(id);
