-- 创建库
create database if not exists campus_performance_statistics_system;

-- 切换库
use campus_performance_statistics_system;

-- ===================== 用户表 =====================
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    user_account varchar(256)                           not null comment '账号',
    user_password varchar(512)                           not null comment '密码',
    user_name     varchar(256)                           null comment '用户昵称',
    user_role     varchar(256) default 'user'            not null comment '用户角色：user/admin',
    edit_time     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    create_time   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete     tinyint      default 0                 not null comment '是否删除',
    UNIQUE KEY uk_user_account (user_account),
    INDEX idx_user_name (user_name)
) comment '用户' collate = utf8mb4_unicode_ci;

-- 密码是 12345678(MD5 加密 + 盐值 yupi)
INSERT INTO user (id, userAccount, userPassword, userName,  userRole) VALUES
(1, 'admin', '10670d38ec32fa8102be6a37f8cb52bf', '管理员',  'admin'),
(2, 'user', '10670d38ec32fa8102be6a37f8cb52bf', '普通用户', 'user');

-- ===================== 教师获奖记录表 =====================
-- 由原 competition_record 表重构而来
-- 移除了 category_id、activity_type_id（分类和活动类型已硬编码）
-- 审核字段拆分到 teacher_competition_audit_record 表
DROP TABLE IF EXISTS competition_record;
DROP TABLE IF EXISTS teacher_competition_record;
create table if not exists teacher_competition_record
(
    id                   bigint auto_increment comment 'id' primary key,
    type_name            varchar(64)   default '教师获奖'       not null comment '记录类型名称',
    user_id              bigint                                not null comment '填报用户ID',
    competition_name     varchar(256)                          not null comment '比赛全称',
    sponsor_unit         varchar(512)                          null comment '颁奖/主办单位',
    competition_rank     varchar(32)                           not null comment '获奖级别：校级/区级/国家级',
    grade_name           varchar(32)                           not null comment '等级：一等奖/二等奖/三等奖/优秀奖/未获奖',
    base_score           decimal(5,2)                          not null comment '基础总分',
    team_member_num      int          default 1                not null comment '参赛总人数（1=单人参赛）',
    first_author_id      bigint                                null comment '第一负责人用户ID（关联user）',
    other_author_ids     varchar(1024)                         null comment '其他参赛教师ID，逗号分隔',
    proof_image_data     longtext                              null comment '参赛/获奖证明图片（base64数据）',
    score_data           text                                  null comment '前端计算的团队成员得分JSON: [{"userId":1,"score":0.75},...]，不含负责人基础2分',
    create_time          datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time          datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete            tinyint     default 0                 not null comment '是否删除',
    INDEX idx_user_id (user_id),
    INDEX idx_type_name (type_name)
) comment '教师获奖记录' collate = utf8mb4_unicode_ci;

-- ===================== 获奖审核记录表--所有分类的比赛均适用 =====================
CREATE TABLE IF NOT EXISTS competition_audit_record
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '审核记录ID',
    record_id            BIGINT       NOT NULL COMMENT '关联比赛记录ID',
    record_type          VARCHAR(64)  DEFAULT '教师获奖'       NOT NULL COMMENT '记录类型名称',
    auto_review_status   VARCHAR(32)  DEFAULT 'PENDING'         NOT NULL COMMENT '自动审核状态：PENDING-待审核, PASSED-通过, FAILED-失败',
    auto_review_comment  VARCHAR(1024)                         NULL COMMENT 'AI自动审核分析意见',
    admin_review_status  VARCHAR(32)  DEFAULT 'PENDING'         NOT NULL COMMENT '管理员审核状态：PENDING-待审核, PASSED-通过, FAILED-失败',
    admin_review_comment VARCHAR(1024)                         NULL COMMENT '管理员审核意见',
    admin_id             BIGINT                                NULL COMMENT '审核管理员ID',
    admin_review_time    DATETIME                              NULL COMMENT '管理员审核时间',
    create_time          DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time          DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete            TINYINT      DEFAULT 0                 NOT NULL COMMENT '是否删除',
    INDEX idx_record_id (record_id),
    INDEX idx_record_type (record_type),
    INDEX idx_auto_review_status (auto_review_status),
    INDEX idx_admin_review_status (admin_review_status)
) COMMENT '教师获奖审核记录' COLLATE utf8mb4_unicode_ci;


-- ===================== 教师个人得分明细表 =====================
DROP TABLE IF EXISTS user_competition_score;
CREATE TABLE IF NOT EXISTS user_competition_score
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id       BIGINT NOT NULL COMMENT '关联比赛记录ID',
    user_id         BIGINT NOT NULL COMMENT '教师用户ID',
    personal_score  DECIMAL(6,3) NOT NULL COMMENT '本次竞赛该教师个人得分',
    is_leader       TINYINT DEFAULT 0 NOT NULL COMMENT '是否负责人：1是0否',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    is_delete       TINYINT DEFAULT 0 NOT NULL,
    UNIQUE KEY uk_record_user(record_id, user_id),
    INDEX idx_record_id(record_id),
    INDEX idx_user_id(user_id)
) COMMENT '教师单场竞赛个人得分明细' COLLATE utf8mb4_unicode_ci;

-- 为 user_competition_score 表新增 type_name 字段（比赛分类冗余字段，方便统计查询）
ALTER TABLE user_competition_score
    ADD COLUMN type_name VARCHAR(64) DEFAULT '教师获奖' NOT NULL COMMENT '比赛分类（冗余字段，方便统计查询）' AFTER user_id,
    ADD INDEX idx_type_name (type_name);

-- 回填已有数据：根据关联的比赛记录同步 type_name
UPDATE user_competition_score s
    JOIN teacher_competition_record r ON s.record_id = r.id
SET s.type_name = r.type_name
WHERE s.type_name = '教师获奖';

-- ===================== 指导学生科技竞赛记录表（v3） =====================
DROP TABLE IF EXISTS student_competition_record;
CREATE TABLE IF NOT EXISTS student_competition_record
(
    id                   BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    type_name            VARCHAR(64)   DEFAULT '指导学生科技竞赛' NOT NULL COMMENT '记录类型名称',
    user_id              BIGINT                                NOT NULL COMMENT '填报用户ID',
    competition_name     VARCHAR(256)                          NOT NULL COMMENT '竞赛名称',
    sponsor_unit         VARCHAR(512)                          NULL COMMENT '主办单位',
    competition_topic    VARCHAR(512)                          NULL COMMENT '参赛题目/赛道（组织者行填"组织者"）',
    student_names        VARCHAR(1024)                         NULL COMMENT '参赛队员姓名',
    competition_rank     VARCHAR(32)                           NULL COMMENT '竞赛等级：院级/校级/区级/自治区级/国家级/行业性全国/行业性省级',
    grade_name           VARCHAR(32)                           NULL COMMENT '获奖等级：一等奖/二等奖/三等奖/优秀奖/未获奖/奖项未出（组织者行为NULL）',
    award_level_text     VARCHAR(512)                          NULL COMMENT '完整获奖级别文本（如"国赛二等奖、省赛一等奖"）',
    award_details        TEXT                                  NULL COMMENT '获奖明细JSON数组：[{"rank":"国家级","grade":"二等奖"},{"rank":"自治区级","grade":"一等奖"}]',
    is_organizer         TINYINT      DEFAULT 0                NOT NULL COMMENT '是否为组织者行：0-指导者行，1-组织者行',
    advisor_score_data   TEXT                                  NULL COMMENT '指导老师得分JSON数组',
    proof_image_data     LONGTEXT                              NULL COMMENT '参赛/获奖证明图片（base64数据）',
    create_time          DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time          DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete            TINYINT      DEFAULT 0                 NOT NULL COMMENT '是否删除',
    INDEX idx_user_id (user_id),
    INDEX idx_type_name (type_name),
    INDEX idx_competition_name (competition_name)
) COMMENT '指导学生科技竞赛记录' COLLATE utf8mb4_unicode_ci;


-- ===================== 指导老师得分明细表（v3） =====================
DROP TABLE IF EXISTS advisor_score;
CREATE TABLE IF NOT EXISTS advisor_score
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id       BIGINT NOT NULL COMMENT '关联记录ID',
    user_id         BIGINT NULL COMMENT '教师用户ID（可为空，仅通过姓名匹配）',
    teacher_name    VARCHAR(128) NOT NULL COMMENT '教师姓名（冗余，方便导出）',
    base_score      DECIMAL(6,3) NOT NULL COMMENT '基础分（组织者/指导者基础分）',
    bonus_score     DECIMAL(6,3) NOT NULL COMMENT '获奖加分',
    total_score     DECIMAL(6,3) NOT NULL COMMENT '总得分 = baseScore + bonusScore',
    is_leader       TINYINT DEFAULT 0 NOT NULL COMMENT '是否主持者：1是0否',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    is_delete       TINYINT DEFAULT 0 NOT NULL,
    INDEX idx_record_id (record_id),
    INDEX idx_user_id (user_id),
    INDEX idx_teacher_name (teacher_name)
) COMMENT '指导老师得分明细' COLLATE utf8mb4_unicode_ci;
