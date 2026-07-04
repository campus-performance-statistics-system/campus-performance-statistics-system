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

-- ===================== 论文业绩记录表（v8） =====================
DROP TABLE IF EXISTS thesis_record;
CREATE TABLE IF NOT EXISTS thesis_record
(
    id                BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    type_name         VARCHAR(64)   DEFAULT '论文业绩' NOT NULL COMMENT '记录类型名称',
    user_id           BIGINT                                NOT NULL COMMENT '填报用户ID',
    thesis_name       VARCHAR(512)                          NOT NULL COMMENT '论文名称',
    journal_name      VARCHAR(512)                          NULL COMMENT '发表刊物',
    thesis_level      VARCHAR(32)                           NOT NULL COMMENT '论文等级：level_1-一级, level_2-二级, level_3-三级, level_4-四级',
    authors_data      TEXT                                  NULL COMMENT '作者及得分分配JSON数组',
    score_data        TEXT                                  NULL COMMENT '得分明细JSON数组',
    proof_image_data  LONGTEXT                              NULL COMMENT '证明图片（base64数据）',
    create_time       DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time       DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete         TINYINT      DEFAULT 0                 NOT NULL COMMENT '是否删除',
    INDEX idx_user_id (user_id),
    INDEX idx_type_name (type_name),
    INDEX idx_thesis_level (thesis_level),
    INDEX idx_thesis_name (thesis_name)
) COMMENT '论文业绩记录' COLLATE utf8mb4_unicode_ci;

-- ===================== 论文业绩得分明细表（v8） =====================
DROP TABLE IF EXISTS thesis_score;
CREATE TABLE IF NOT EXISTS thesis_score
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id       BIGINT NOT NULL COMMENT '关联记录ID',
    user_id         BIGINT NULL COMMENT '教师用户ID（可为空，仅通过姓名匹配）',
    teacher_name    VARCHAR(128) NOT NULL COMMENT '教师姓名（冗余，方便导出）',
    score           DECIMAL(6,3) NOT NULL COMMENT '得分',
    is_first_author TINYINT DEFAULT 0 NOT NULL COMMENT '是否第一作者：1是0否',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    is_delete       TINYINT  DEFAULT 0 NOT NULL,
    INDEX idx_record_id (record_id),
    INDEX idx_user_id (user_id),
    INDEX idx_teacher_name (teacher_name)
) COMMENT '论文业绩得分明细' COLLATE utf8mb4_unicode_ci;

-- ===================== 体育比赛业绩记录表（v9） =====================
DROP TABLE IF EXISTS sports_event_record;
CREATE TABLE IF NOT EXISTS sports_event_record
(
    id                BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    type_name         VARCHAR(64)   DEFAULT '体育比赛业绩' NOT NULL COMMENT '记录类型名称',
    user_id           BIGINT                                NOT NULL COMMENT '填报用户ID',
    event_name        VARCHAR(255)                          NOT NULL COMMENT '比赛项目名称',
    event_type        VARCHAR(32)                           NOT NULL COMMENT '项目类型：track_field-运动会项目, ball_game-球类项目',
    event_result      VARCHAR(32)                           NULL COMMENT '球类项目结果：champion-冠军, placed-获奖, participated-参与',
    member_data       TEXT                                  NULL COMMENT '参与教师及名次JSON数组',
    score_data        TEXT                                  NULL COMMENT '得分明细JSON数组',
    proof_image_data  LONGTEXT                              NULL COMMENT '证明图片（base64数据）',
    create_time       DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time       DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete         TINYINT      DEFAULT 0                 NOT NULL COMMENT '是否删除',
    INDEX idx_user_id (user_id),
    INDEX idx_event_type (event_type),
    INDEX idx_event_name (event_name)
) COMMENT '体育比赛业绩记录' COLLATE utf8mb4_unicode_ci;

-- ===================== 体育比赛业绩得分明细表（v9） =====================
DROP TABLE IF EXISTS sports_event_score;
CREATE TABLE IF NOT EXISTS sports_event_score
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id    BIGINT NOT NULL COMMENT '关联记录ID',
    user_id      BIGINT NULL COMMENT '教师用户ID（可为空，仅通过姓名匹配）',
    teacher_name VARCHAR(128) NOT NULL COMMENT '教师姓名（冗余，方便导出）',
    score        DECIMAL(6,3) NOT NULL COMMENT '得分',
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    is_delete    TINYINT  DEFAULT 0 NOT NULL,
    INDEX idx_record_id (record_id),
    INDEX idx_user_id (user_id),
    INDEX idx_teacher_name (teacher_name)
) COMMENT '体育比赛业绩得分明细' COLLATE utf8mb4_unicode_ci;

-- ===================== 兼职班主任业绩记录表（v10） =====================
DROP TABLE IF EXISTS part_time_class_advisor_record;
CREATE TABLE IF NOT EXISTS part_time_class_advisor_record
(
    id                            BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    type_name                     VARCHAR(64)   DEFAULT '兼职班主任'   NOT NULL COMMENT '记录类型名称',
    user_id                       BIGINT                                NOT NULL COMMENT '填报用户ID',
    teacher_name                  VARCHAR(128)                          NOT NULL COMMENT '教师姓名',
    class_id                      VARCHAR(64)                           NOT NULL COMMENT '负责班级编号',
    study_style_work_req          DECIMAL(5,1)  DEFAULT 0               NULL COMMENT '学风建设-工作要求（满分20）',
    study_style_effect            DECIMAL(5,1)  DEFAULT 0               NULL COMMENT '学风建设-效果评估（满分10）',
    safety_edu_work_req           DECIMAL(5,1)  DEFAULT 0               NULL COMMENT '安全教育-工作要求（满分20）',
    safety_edu_effect             DECIMAL(5,1)  DEFAULT 0               NULL COMMENT '安全教育-效果评估（满分10）',
    struggling_student_work_req   DECIMAL(5,1)  DEFAULT 0               NULL COMMENT '后进生帮扶-工作要求（满分20）',
    struggling_student_effect     DECIMAL(5,1)  DEFAULT 0               NULL COMMENT '后进生帮扶-效果评估（满分10）',
    achievement_safety            DECIMAL(5,1)  DEFAULT 0               NULL COMMENT '育人成果-安全稳定（满分3）',
    achievement_study_style       DECIMAL(5,1)  DEFAULT 0               NULL COMMENT '育人成果-学风建设（满分3）',
    achievement_struggling        DECIMAL(5,1)  DEFAULT 0               NULL COMMENT '育人成果-后进生帮扶（满分4）',
    admin_class_score             DECIMAL(4,2)                          NULL COMMENT '行政班分（1.0或0.5）',
    is_freshmen_or_graduating     TINYINT       DEFAULT 0               NOT NULL COMMENT '是否新生或毕业班：0-普通班级，1-新生/毕业班',
    proof_image_data              LONGTEXT                              NULL COMMENT '证明图片（base64数据）',
    create_time                   DATETIME      DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time                   DATETIME      DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete                     TINYINT       DEFAULT 0                 NOT NULL COMMENT '是否删除',
    INDEX idx_user_id (user_id),
    INDEX idx_type_name (type_name),
    INDEX idx_teacher_name (teacher_name)
) COMMENT '兼职班主任业绩记录' COLLATE utf8mb4_unicode_ci;

-- ===================== 兼职班主任业绩得分明细表（v10） =====================
DROP TABLE IF EXISTS part_time_class_advisor_score;
CREATE TABLE IF NOT EXISTS part_time_class_advisor_score
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id    BIGINT NOT NULL COMMENT '关联记录ID',
    user_id      BIGINT NULL COMMENT '教师用户ID（可为空，仅通过姓名匹配）',
    teacher_name VARCHAR(128) NOT NULL COMMENT '教师姓名（冗余，方便导出）',
    score        DECIMAL(6,3) NOT NULL COMMENT '得分',
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    is_delete    TINYINT  DEFAULT 0 NOT NULL,
    INDEX idx_record_id (record_id),
    INDEX idx_user_id (user_id),
    INDEX idx_teacher_name (teacher_name)
) COMMENT '兼职班主任业绩得分明细' COLLATE utf8mb4_unicode_ci;

-- ===================== 监考次数统计记录表（v11） =====================
DROP TABLE IF EXISTS invigilation_record;
CREATE TABLE IF NOT EXISTS invigilation_record
(
    id                  BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    type_name           VARCHAR(64)   DEFAULT '监考次数统计' NOT NULL COMMENT '记录类型名称',
    user_id             BIGINT                                NOT NULL COMMENT '填报用户ID',
    teacher_name        VARCHAR(128)                          NOT NULL COMMENT '监考老师姓名',
    invigilation_count  INT           DEFAULT 0               NOT NULL COMMENT '监考次数',
    proof_image_data    LONGTEXT                              NULL COMMENT '证明图片（base64数据）',
    create_time         DATETIME      DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time         DATETIME      DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete           TINYINT       DEFAULT 0                 NOT NULL COMMENT '是否删除',
    INDEX idx_user_id (user_id),
    INDEX idx_type_name (type_name),
    INDEX idx_teacher_name (teacher_name)
) COMMENT '监考次数统计记录' COLLATE utf8mb4_unicode_ci;

-- ===================== 网上评教记录表（v12） =====================
DROP TABLE IF EXISTS online_evaluation_record;
CREATE TABLE IF NOT EXISTS online_evaluation_record
(
    id                  BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    type_name           VARCHAR(64)   DEFAULT '网上评教'       NOT NULL COMMENT '记录类型名称',
    user_id             BIGINT                                NOT NULL COMMENT '填报用户ID',
    teacher_name        VARCHAR(128)                          NOT NULL COMMENT '教师姓名',
    teacher_type        VARCHAR(32)   DEFAULT '专任教师'      NOT NULL COMMENT '教师类型：专任教师/外聘教师',
    academic_year       VARCHAR(32)                           NOT NULL COMMENT '学年（如2022-2023）',
    semester            VARCHAR(32)                           NOT NULL COMMENT '学期（如第一学期/第二学期）',
    course_code         VARCHAR(128)                          NOT NULL COMMENT '课程序号',
    course_name         VARCHAR(256)                          NOT NULL COMMENT '课程名称',
    participant_count   INT           DEFAULT 0               NOT NULL COMMENT '参评人数',
    average_score       DECIMAL(5,2)  DEFAULT 0.00            NOT NULL COMMENT '平均分',
    proof_image_data    LONGTEXT                              NULL COMMENT '证明图片（base64数据）',
    create_time         DATETIME      DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time         DATETIME      DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete           TINYINT       DEFAULT 0                 NOT NULL COMMENT '是否删除',
    INDEX idx_user_id (user_id),
    INDEX idx_type_name (type_name),
    INDEX idx_teacher_name (teacher_name),
    INDEX idx_teacher_type (teacher_type),
    INDEX idx_academic_year (academic_year)
) COMMENT '网上评教记录' COLLATE utf8mb4_unicode_ci;

-- ===================== 推荐学院学生签约就业记录表（v13） =====================
DROP TABLE IF EXISTS recommended_employment_record;
CREATE TABLE IF NOT EXISTS recommended_employment_record
(
    id                  BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    type_name           VARCHAR(64)   DEFAULT '推荐学院学生签约就业' NOT NULL COMMENT '记录类型名称',
    user_id             BIGINT                                NOT NULL COMMENT '填报用户ID',
    teacher_name        VARCHAR(128)                          NOT NULL COMMENT '联系人（教师姓名）',
    company_name        VARCHAR(512)                          NOT NULL COMMENT '单位名称',
    contract_count      INT           DEFAULT 0               NOT NULL COMMENT '签约数',
    recommendation_time VARCHAR(128)                          NULL COMMENT '推荐时间（如2023年3月）',
    proof_image_data    LONGTEXT                              NULL COMMENT '证明图片（base64数据）',
    create_time         DATETIME      DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time         DATETIME      DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete           TINYINT       DEFAULT 0                 NOT NULL COMMENT '是否删除',
    INDEX idx_user_id (user_id),
    INDEX idx_type_name (type_name),
    INDEX idx_teacher_name (teacher_name)
) COMMENT '推荐学院学生签约就业记录' COLLATE utf8mb4_unicode_ci;

-- ----------------------------
-- v14：签订合作企业
-- ----------------------------
DROP TABLE IF EXISTS cooperative_enterprise_record;
CREATE TABLE IF NOT EXISTS cooperative_enterprise_record
(
    id               BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    type_name        VARCHAR(64)   DEFAULT '签订合作企业'           NOT NULL COMMENT '记录类型名称',
    user_id          BIGINT                                NOT NULL COMMENT '填报用户ID',
    college          VARCHAR(256)                          NOT NULL COMMENT '学院',
    enterprise_name  VARCHAR(512)                          NOT NULL COMMENT '企业名称',
    teacher_name     VARCHAR(128)                          NOT NULL COMMENT '签订合作企业老师',
    proof_image_data LONGTEXT                              NULL COMMENT '证明图片（base64数据）',
    create_time      DATETIME      DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time      DATETIME      DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete        TINYINT       DEFAULT 0                 NOT NULL COMMENT '是否删除',
    INDEX idx_user_id (user_id),
    INDEX idx_type_name (type_name),
    INDEX idx_teacher_name (teacher_name),
    INDEX idx_college (college),
    INDEX idx_enterprise_name (enterprise_name)
) COMMENT '签订合作企业记录' COLLATE utf8mb4_unicode_ci;

-- ----------------------------
-- v15：指导青年教师
-- ----------------------------
DROP TABLE IF EXISTS young_teacher_guidance_record;
CREATE TABLE IF NOT EXISTS young_teacher_guidance_record
(
    id                      BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    type_name               VARCHAR(64)   DEFAULT '指导青年教师'       NOT NULL COMMENT '记录类型名称',
    user_id                 BIGINT                                NOT NULL COMMENT '填报用户ID',
    college                 VARCHAR(256)                          NOT NULL COMMENT '教学单位（学院）',
    mentor_names            VARCHAR(512)                          NOT NULL COMMENT '指导教师-姓名（多个用、分隔）',
    mentor_titles           VARCHAR(512)                          NOT NULL COMMENT '指导教师-职称（多个用、分隔，与姓名一一对应）',
    young_teacher_name      VARCHAR(128)                          NOT NULL COMMENT '青年教师-姓名',
    young_teacher_entry_time VARCHAR(32)                          NULL COMMENT '青年教师-入职时间（格式YYYY.MM）',
    proof_image_data        LONGTEXT                              NULL COMMENT '证明图片（base64数据）',
    create_time             DATETIME      DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time             DATETIME      DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete               TINYINT       DEFAULT 0                 NOT NULL COMMENT '是否删除',
    INDEX idx_user_id (user_id),
    INDEX idx_type_name (type_name),
    INDEX idx_college (college),
    INDEX idx_mentor_names (mentor_names(191)),
    INDEX idx_young_teacher_name (young_teacher_name)
) COMMENT '指导青年教师记录' COLLATE utf8mb4_unicode_ci;

-- ----------------------------
-- v16：2023年优秀毕设
-- ----------------------------
DROP TABLE IF EXISTS excellent_graduation_project_record;
CREATE TABLE IF NOT EXISTS excellent_graduation_project_record
(
    id               BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    type_name        VARCHAR(64)    DEFAULT '优秀毕设'          NOT NULL COMMENT '记录类型名称',
    user_id          BIGINT                                NOT NULL COMMENT '填报用户ID',
    major            VARCHAR(128)                           NULL COMMENT '专业',
    student_id       VARCHAR(64)                            NULL COMMENT '学号',
    student_name     VARCHAR(64)                            NULL COMMENT '学生姓名',
    project_title    VARCHAR(512)                           NULL COMMENT '毕设题目',
    advisor_name     VARCHAR(128)                           NULL COMMENT '指导教师',
    `rank`           INT                                    NULL COMMENT '名次',
    proof_image_data LONGTEXT                              NULL COMMENT '证明图片（base64数据）',
    create_time      DATETIME      DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time      DATETIME      DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete        TINYINT       DEFAULT 0                 NOT NULL COMMENT '是否删除',
    INDEX idx_user_id (user_id),
    INDEX idx_type_name (type_name),
    INDEX idx_major (major),
    INDEX idx_student_name (student_name),
    INDEX idx_advisor_name (advisor_name)
) COMMENT '优秀毕设记录表（v16）' COLLATE utf8mb4_unicode_ci;

-- ----------------------------
-- v17：校企联合培养
-- ----------------------------
DROP TABLE IF EXISTS school_enterprise_training_record;
CREATE TABLE IF NOT EXISTS school_enterprise_training_record
(
    id                        BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    type_name                 VARCHAR(64)    DEFAULT '校企联合培养'     NOT NULL COMMENT '记录类型名称',
    user_id                   BIGINT                                NOT NULL COMMENT '填报用户ID',
    student_name              VARCHAR(64)                            NULL COMMENT '学生姓名',
    student_id                VARCHAR(64)                            NULL COMMENT '学号',
    major                     VARCHAR(128)                           NULL COMMENT '专业',
    company_name              VARCHAR(256)                           NULL COMMENT '公司名称',
    remark                    VARCHAR(128)                           NULL COMMENT '备注（3+0.5+0.5或3+1等）',
    project_collection_status VARCHAR(128)                           NULL COMMENT '企业毕设收集情况',
    advisor_name              VARCHAR(128)                           NULL COMMENT '校内指导老师',
    counselor_name            VARCHAR(128)                           NULL COMMENT '校内辅导员',
    proof_image_data          LONGTEXT                              NULL COMMENT '证明图片（base64数据）',
    create_time               DATETIME      DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time               DATETIME      DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete                 TINYINT       DEFAULT 0                 NOT NULL COMMENT '是否删除',
    INDEX idx_user_id (user_id),
    INDEX idx_type_name (type_name),
    INDEX idx_student_name (student_name),
    INDEX idx_major (major),
    INDEX idx_company_name (company_name),
    INDEX idx_advisor_name (advisor_name)
) COMMENT '校企联合培养记录表（v17）' COLLATE utf8mb4_unicode_ci;

-- ===================== 外键约束：得分明细表级联删除 =====================
-- 当主表比赛记录被删除时，对应的得分明细记录也自动删除

-- 教师获奖 → 教师个人得分明细
ALTER TABLE user_competition_score
    ADD CONSTRAINT fk_user_competition_score_record
        FOREIGN KEY (record_id) REFERENCES teacher_competition_record (id)
            ON DELETE CASCADE;

-- 指导学生科技竞赛 → 指导老师得分明细
ALTER TABLE advisor_score
    ADD CONSTRAINT fk_advisor_score_record
        FOREIGN KEY (record_id) REFERENCES student_competition_record (id)
            ON DELETE CASCADE;

-- 指导实训 → 指导实训得分明细
ALTER TABLE training_guidance_score
    ADD CONSTRAINT fk_training_guidance_score_record
        FOREIGN KEY (record_id) REFERENCES training_guidance_record (id)
            ON DELETE CASCADE;

-- 科研及教材业绩 → 科研及教材业绩得分明细
ALTER TABLE research_achievement_score
    ADD CONSTRAINT fk_research_achievement_score_record
        FOREIGN KEY (record_id) REFERENCES research_achievement_record (id)
            ON DELETE CASCADE;

-- 大创业绩 → 大创业绩得分明细
ALTER TABLE innovation_entrepreneurship_score
    ADD CONSTRAINT fk_innovation_entrepreneurship_score_record
        FOREIGN KEY (record_id) REFERENCES innovation_entrepreneurship_record (id)
            ON DELETE CASCADE;

-- 教改科研项目业绩 → 教改科研项目业绩得分明细
ALTER TABLE teaching_reform_score
    ADD CONSTRAINT fk_teaching_reform_score_record
        FOREIGN KEY (record_id) REFERENCES teaching_reform_record (id)
            ON DELETE CASCADE;

-- 论文业绩 → 论文业绩得分明细
ALTER TABLE thesis_score
    ADD CONSTRAINT fk_thesis_score_record
        FOREIGN KEY (record_id) REFERENCES thesis_record (id)
            ON DELETE CASCADE;

-- 体育比赛业绩 → 体育比赛业绩得分明细
ALTER TABLE sports_event_score
    ADD CONSTRAINT fk_sports_event_score_record
        FOREIGN KEY (record_id) REFERENCES sports_event_record (id)
            ON DELETE CASCADE;

-- 兼职班主任 → 兼职班主任业绩得分明细
ALTER TABLE part_time_class_advisor_score
    ADD CONSTRAINT fk_part_time_class_advisor_score_record
        FOREIGN KEY (record_id) REFERENCES part_time_class_advisor_record (id)
            ON DELETE CASCADE;

-- ===================== 触发器：删除比赛记录时级联删除审核记录 =====================
-- competition_audit_record 的 record_id 是多态关联，无法用外键约束，通过触发器实现级联删除

DROP TRIGGER IF EXISTS trg_teacher_competition_record_delete;
CREATE TRIGGER trg_teacher_competition_record_delete
    BEFORE DELETE ON teacher_competition_record
    FOR EACH ROW
BEGIN
    DELETE FROM competition_audit_record WHERE record_id = OLD.id AND record_type = '教师获奖';
END;

DROP TRIGGER IF EXISTS trg_student_competition_record_delete;
CREATE TRIGGER trg_student_competition_record_delete
    BEFORE DELETE ON student_competition_record
    FOR EACH ROW
BEGIN
    DELETE FROM competition_audit_record WHERE record_id = OLD.id AND record_type = '指导学生科技竞赛';
END;

DROP TRIGGER IF EXISTS trg_training_guidance_record_delete;
CREATE TRIGGER trg_training_guidance_record_delete
    BEFORE DELETE ON training_guidance_record
    FOR EACH ROW
BEGIN
    DELETE FROM competition_audit_record WHERE record_id = OLD.id AND record_type = '指导实训';
END;

DROP TRIGGER IF EXISTS trg_research_achievement_record_delete;
CREATE TRIGGER trg_research_achievement_record_delete
    BEFORE DELETE ON research_achievement_record
    FOR EACH ROW
BEGIN
    DELETE FROM competition_audit_record WHERE record_id = OLD.id AND record_type = '科研及教材业绩';
END;

DROP TRIGGER IF EXISTS trg_innovation_entrepreneurship_record_delete;
CREATE TRIGGER trg_innovation_entrepreneurship_record_delete
    BEFORE DELETE ON innovation_entrepreneurship_record
    FOR EACH ROW
BEGIN
    DELETE FROM competition_audit_record WHERE record_id = OLD.id AND record_type = '大创业绩';
END;

DROP TRIGGER IF EXISTS trg_teaching_reform_record_delete;
CREATE TRIGGER trg_teaching_reform_record_delete
    BEFORE DELETE ON teaching_reform_record
    FOR EACH ROW
BEGIN
    DELETE FROM competition_audit_record WHERE record_id = OLD.id AND record_type = '教改科研项目业绩';
END;

DROP TRIGGER IF EXISTS trg_thesis_record_delete;
CREATE TRIGGER trg_thesis_record_delete
    BEFORE DELETE ON thesis_record
    FOR EACH ROW
BEGIN
    DELETE FROM competition_audit_record WHERE record_id = OLD.id AND record_type = '论文业绩';
END;

DROP TRIGGER IF EXISTS trg_sports_event_record_delete;
CREATE TRIGGER trg_sports_event_record_delete
    BEFORE DELETE ON sports_event_record
    FOR EACH ROW
BEGIN
    DELETE FROM competition_audit_record WHERE record_id = OLD.id AND record_type = '体育比赛业绩';
END;

DROP TRIGGER IF EXISTS trg_part_time_class_advisor_record_delete;
CREATE TRIGGER trg_part_time_class_advisor_record_delete
    BEFORE DELETE ON part_time_class_advisor_record
    FOR EACH ROW
BEGIN
    DELETE FROM competition_audit_record WHERE record_id = OLD.id AND record_type = '兼职班主任';
END;
