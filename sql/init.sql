-- 创建库
create database if not exists campus_performance_statistics_system;

-- 切换库
use campus_performance_statistics_system;

-- ===================== 用户表 =====================
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName)
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

-- ===================== 教师获奖审核记录表 =====================
-- 从 competition_record 表拆分出的审核相关字段
DROP TABLE IF EXISTS teacher_competition_audit_record;
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

-- ===================== 已废弃的表（不再使用） =====================
DROP TABLE IF EXISTS activity_rank_grade_score;
DROP TABLE IF EXISTS activity_type;
DROP TABLE IF EXISTS category;
