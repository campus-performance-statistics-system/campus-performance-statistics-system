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
INSERT INTO user (id, user_account, user_password, user_name,  user_role) VALUES
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

-- ===================== 指导实训记录表（v4） =====================
DROP TABLE IF EXISTS training_guidance_record;
CREATE TABLE IF NOT EXISTS training_guidance_record
(
    id                     BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    type_name              VARCHAR(64)   DEFAULT '指导实训'       NOT NULL COMMENT '记录类型名称',
    user_id                BIGINT                                NOT NULL COMMENT '填报用户ID',
    semester               VARCHAR(64)                           NOT NULL COMMENT '学期',
    training_name          VARCHAR(256)                          NOT NULL COMMENT '实训名称',
    responsible_teachers   TEXT                                  NULL COMMENT '负责教师JSON数组：[{"teacherName":"秦小旭"},...]',
    participating_teachers TEXT                                  NULL COMMENT '参与教师JSON数组：[{"teacherName":"李志平"},...]',
    proof_image_data       LONGTEXT                              NULL COMMENT '证明图片（base64数据）',
    create_time            DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time            DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete              TINYINT      DEFAULT 0                 NOT NULL COMMENT '是否删除',
    INDEX idx_user_id (user_id),
    INDEX idx_type_name (type_name),
    INDEX idx_training_name (training_name)
) COMMENT '指导实训记录' COLLATE utf8mb4_unicode_ci;

-- ===================== 指导实训得分明细表（v4） =====================
DROP TABLE IF EXISTS training_guidance_score;
CREATE TABLE IF NOT EXISTS training_guidance_score
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id    BIGINT NOT NULL COMMENT '关联记录ID',
    user_id      BIGINT NULL COMMENT '教师用户ID（可为空，仅通过姓名匹配）',
    teacher_name VARCHAR(128) NOT NULL COMMENT '教师姓名（冗余，方便导出）',
    score        DECIMAL(6,3) NOT NULL COMMENT '得分（负责教师2分，参与教师1分）',
    role_type    VARCHAR(32)  NOT NULL COMMENT '角色类型：responsible-负责教师, participating-参与教师',
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    is_delete    TINYINT  DEFAULT 0 NOT NULL,
    INDEX idx_record_id (record_id),
    INDEX idx_user_id (user_id),
    INDEX idx_teacher_name (teacher_name)
) COMMENT '指导实训得分明细' COLLATE utf8mb4_unicode_ci;

-- ===================== 科研及教材业绩记录表（v5） =====================
DROP TABLE IF EXISTS research_achievement_record;
CREATE TABLE IF NOT EXISTS research_achievement_record
(
    id                BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    type_name         VARCHAR(64)   DEFAULT '科研及教材业绩' NOT NULL COMMENT '记录类型名称',
    sub_type          VARCHAR(32)                           NOT NULL COMMENT '子类型：horizontal_project-横向科研项目, patent-专利, textbook-教材及自编讲义',
    user_id           BIGINT                                NOT NULL COMMENT '填报用户ID',
    achievement_name  VARCHAR(256)                          NOT NULL COMMENT '成果名称（项目名称/专利名称/教材名称）',
    project_source    VARCHAR(512)                          NULL COMMENT '项目来源（横向科研项目）',
    funding_amount    DECIMAL(12,2)                         NULL COMMENT '到位经费-万元（横向科研项目）',
    patent_number     VARCHAR(128)                          NULL COMMENT '专利号',
    patent_type       VARCHAR(32)                           NULL COMMENT '专利类别：invention-发明专利, utility_model-实用新型',
    word_count        DECIMAL(8,2)                          NULL COMMENT '字数-万（教材）',
    textbook_type     VARCHAR(32)                           NULL COMMENT '教材类型：published-出版教材, first_handout-首次自编讲义, revised_handout-修改讲义',
    member_data       TEXT                                  NULL COMMENT '项目组成员及得分分配JSON数组',
    score_data        TEXT                                  NULL COMMENT '得分明细JSON数组',
    proof_image_data  LONGTEXT                              NULL COMMENT '证明图片（base64数据）',
    create_time       DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time       DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete         TINYINT      DEFAULT 0                 NOT NULL COMMENT '是否删除',
    INDEX idx_user_id (user_id),
    INDEX idx_type_name (type_name),
    INDEX idx_sub_type (sub_type)
) COMMENT '科研及教材业绩记录' COLLATE utf8mb4_unicode_ci;

-- ===================== 科研及教材业绩得分明细表（v5） =====================
DROP TABLE IF EXISTS research_achievement_score;
CREATE TABLE IF NOT EXISTS research_achievement_score
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id    BIGINT NOT NULL COMMENT '关联记录ID',
    user_id      BIGINT NULL COMMENT '教师用户ID（可为空，仅通过姓名匹配）',
    teacher_name VARCHAR(128) NOT NULL COMMENT '教师姓名（冗余，方便导出）',
    score        DECIMAL(6,3) NOT NULL COMMENT '得分',
    is_leader    TINYINT DEFAULT 0 NOT NULL COMMENT '是否负责人：1是0否',
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    is_delete    TINYINT  DEFAULT 0 NOT NULL,
    INDEX idx_record_id (record_id),
    INDEX idx_user_id (user_id),
    INDEX idx_teacher_name (teacher_name)
) COMMENT '科研及教材业绩得分明细' COLLATE utf8mb4_unicode_ci;

-- ===================== 大创业绩记录表（v6） =====================
DROP TABLE IF EXISTS innovation_entrepreneurship_record;
CREATE TABLE IF NOT EXISTS innovation_entrepreneurship_record
(
    id                BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    type_name         VARCHAR(64)   DEFAULT '大创业绩' NOT NULL COMMENT '记录类型名称',
    user_id           BIGINT                                NOT NULL COMMENT '填报用户ID',
    project_number    VARCHAR(128)                          NULL COMMENT '项目编号',
    project_name      VARCHAR(256)                          NOT NULL COMMENT '项目名称',
    project_level     VARCHAR(32)                           NOT NULL COMMENT '项目级别：national-国家级, regional-区级',
    project_type      VARCHAR(32)                           NOT NULL COMMENT '项目类型：innovation_training-创新训练, entrepreneurship_training-创业训练, entrepreneurship_practice-创业实践',
    project_status    VARCHAR(32) DEFAULT 'newly_added'     NULL COMMENT '项目状态：concluded-结题, newly_added-新增',
    student_leader    VARCHAR(128)                          NULL COMMENT '项目负责人（学生姓名）',
    member_data       TEXT                                  NULL COMMENT '指导教师成员及得分分配JSON数组',
    score_data        TEXT                                  NULL COMMENT '得分明细JSON数组',
    proof_image_data  LONGTEXT                              NULL COMMENT '证明图片（base64数据）',
    create_time       DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time       DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete         TINYINT      DEFAULT 0                 NOT NULL COMMENT '是否删除',
    INDEX idx_user_id (user_id),
    INDEX idx_type_name (type_name),
    INDEX idx_project_level (project_level),
    INDEX idx_project_name (project_name)
) COMMENT '大创业绩记录' COLLATE utf8mb4_unicode_ci;

-- ===================== 大创业绩得分明细表（v6） =====================
DROP TABLE IF EXISTS innovation_entrepreneurship_score;
CREATE TABLE IF NOT EXISTS innovation_entrepreneurship_score
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id    BIGINT NOT NULL COMMENT '关联记录ID',
    user_id      BIGINT NULL COMMENT '教师用户ID（可为空，仅通过姓名匹配）',
    teacher_name VARCHAR(128) NOT NULL COMMENT '教师姓名（冗余，方便导出）',
    score        DECIMAL(6,3) NOT NULL COMMENT '得分',
    is_leader    TINYINT DEFAULT 0 NOT NULL COMMENT '是否负责人：1是0否',
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    is_delete    TINYINT  DEFAULT 0 NOT NULL,
    INDEX idx_record_id (record_id),
    INDEX idx_user_id (user_id),
    INDEX idx_teacher_name (teacher_name)
) COMMENT '大创业绩得分明细' COLLATE utf8mb4_unicode_ci;

-- ===================== 教改科研项目业绩记录表（v7） =====================
DROP TABLE IF EXISTS teaching_reform_record;
CREATE TABLE IF NOT EXISTS teaching_reform_record
(
    id                BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    type_name         VARCHAR(64)   DEFAULT '教改科研项目业绩' NOT NULL COMMENT '记录类型名称',
    user_id           BIGINT                                NOT NULL COMMENT '填报用户ID',
    project_name      VARCHAR(256)                          NOT NULL COMMENT '项目名称',
    project_type      VARCHAR(32)                           NOT NULL COMMENT '项目类型：provincial_education_reform-教育厅教改工程, young_teacher_basic-中青年教师基础能力提升, university_research-校级科研, university_course_ideology-校级课程思政',
    project_status    VARCHAR(32) DEFAULT 'approved'        NULL COMMENT '项目状态：approved-新增（获批立项）, concluded-结题, not_approved-未获批, pending_decision-未下文',
    project_leader    VARCHAR(128)                          NULL COMMENT '项目负责人（教师姓名）',
    member_data       TEXT                                  NULL COMMENT '项目组成员及得分分配JSON数组',
    score_data        TEXT                                  NULL COMMENT '得分明细JSON数组',
    proof_image_data  LONGTEXT                              NULL COMMENT '证明图片（base64数据）',
    create_time       DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time       DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete         TINYINT      DEFAULT 0                 NOT NULL COMMENT '是否删除',
    INDEX idx_user_id (user_id),
    INDEX idx_type_name (type_name),
    INDEX idx_project_type (project_type),
    INDEX idx_project_name (project_name)
) COMMENT '教改科研项目业绩记录' COLLATE utf8mb4_unicode_ci;

-- ===================== 教改科研项目业绩得分明细表（v7） =====================
DROP TABLE IF EXISTS teaching_reform_score;
CREATE TABLE IF NOT EXISTS teaching_reform_score
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id    BIGINT NOT NULL COMMENT '关联记录ID',
    user_id      BIGINT NULL COMMENT '教师用户ID（可为空，仅通过姓名匹配）',
    teacher_name VARCHAR(128) NOT NULL COMMENT '教师姓名（冗余，方便导出）',
    score        DECIMAL(6,3) NOT NULL COMMENT '得分',
    is_leader    TINYINT DEFAULT 0 NOT NULL COMMENT '是否负责人：1是0否',
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    is_delete    TINYINT  DEFAULT 0 NOT NULL,
    INDEX idx_record_id (record_id),
    INDEX idx_user_id (user_id),
    INDEX idx_teacher_name (teacher_name)
) COMMENT '教改科研项目业绩得分明细' COLLATE utf8mb4_unicode_ci;
