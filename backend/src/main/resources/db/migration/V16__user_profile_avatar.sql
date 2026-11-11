-- Profile pictures stored in object storage.

ALTER TABLE user_profile ADD COLUMN avatar_object_key VARCHAR(300);
ALTER TABLE user_profile ADD COLUMN avatar_content_type VARCHAR(100);

CREATE INDEX ix_user_profile_avatar ON user_profile (organization_id)
    WHERE avatar_object_key IS NOT NULL;
