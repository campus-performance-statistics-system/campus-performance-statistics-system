-- 为 user_competition_score 表新增 type_name 字段（比赛分类冗余字段，方便统计查询）
ALTER TABLE user_competition_score
    ADD COLUMN type_name VARCHAR(64) DEFAULT '教师获奖' NOT NULL COMMENT '比赛分类（冗余字段，方便统计查询）' AFTER user_id,
    ADD INDEX idx_type_name (type_name);

-- 回填已有数据：根据关联的比赛记录同步 type_name
UPDATE user_competition_score s
    JOIN teacher_competition_record r ON s.record_id = r.id
SET s.type_name = r.type_name
WHERE s.type_name = '教师获奖';
