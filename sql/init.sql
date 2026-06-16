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

-- ===================== 比赛分类表 =====================
create table if not exists category
(
    id          bigint auto_increment comment 'id' primary key,
    name        varchar(256)                          not null comment '分类名称',
    description varchar(512)                          null comment '分类描述',
    create_time datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete   tinyint     default 0                 not null comment '是否删除'
) comment '比赛分类' collate = utf8mb4_unicode_ci;

INSERT INTO category (id, name, description) VALUES
(1, '服务类', '学校组织的各类服务性比赛与活动'),
(2, '个人业务类', '个人参加的竞赛类项目');

-- ===================== 活动类型表 =====================
create table if not exists activity_type
(
    id          bigint auto_increment comment 'id' primary key,
    name        varchar(256)                          not null comment '活动类型名称',
    description varchar(512)                          null comment '活动类型描述',
    category_id bigint                                not null comment '所属分类ID',
    sponsor_unit varchar(512)                         null comment '颁奖/主办单位',
    create_time datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete   tinyint     default 0                 not null comment '是否删除',
    INDEX idx_category_id (category_id)
) comment '活动类型' collate = utf8mb4_unicode_ci;


-- ===================== 活动类型-竞赛等级-获奖等级-得分表 =====================
-- 管理员在新增活动类型时填写：竞赛等级、获奖等级、对应得分
-- 得分示例：
--   参与院级以上比赛：2分/项（负责人）
--   院级获奖每项增加：一等奖2分；其它等级奖1.5分；优秀奖1分
--   自治区级获奖每项增加：二等奖5分；三等奖4分；优秀奖3分
CREATE TABLE IF NOT EXISTS activity_rank_grade_score
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_type_id  BIGINT       NOT NULL COMMENT '关联活动类型ID',
    competition_rank  VARCHAR(32)  NOT NULL COMMENT '竞赛等级：校级/区级/国家级',
    grade_name        VARCHAR(32)  NOT NULL COMMENT '获奖等级：优秀奖/一等奖/二等奖/三等奖/未获奖',
    base_score        DECIMAL(5,2) NOT NULL COMMENT '得分',
    create_time       DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time       DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    is_delete         TINYINT DEFAULT 0 NOT NULL,
    UNIQUE KEY uk_activity_rank_grade(activity_type_id, competition_rank, grade_name),
    FOREIGN KEY fk_activity_type(activity_type_id) REFERENCES activity_type(id),
    INDEX idx_activity_type_id(activity_type_id)
) COMMENT '活动类型-竞赛等级-获奖等级得分规则' COLLATE utf8mb4_unicode_ci;

-- ===================== 比赛记录表 =====================
DROP TABLE IF EXISTS competition_record;
create table if not exists competition_record
(
    id                   bigint auto_increment comment 'id' primary key,
    user_id              bigint                                not null comment '填报用户ID',
    category_id          bigint                                null comment '比赛大类ID',
    activity_type_id     bigint                                null comment '活动细分类型ID',
    competition_name     varchar(256)                          not null comment '比赛全称',
    sponsor_unit         varchar(512)                          null comment '颁奖/主办单位',
    competition_rank     varchar(32)                           not null comment '获奖级别：校级/区级/国家级',
    grade_name           varchar(32)                           not null comment '等级：一等奖/二等奖/三等奖/优秀奖/未获奖',
    base_score           decimal(5,2)                          not null comment '基础总分',
    team_member_num      int          default 1                not null comment '参赛总人数（1=单人参赛）',
    first_author_id      bigint                                null comment '第一负责人用户ID（关联user）',
    other_author_ids     varchar(1024)                         null comment '其他参赛教师ID，逗号分隔',
    proof_image_data     longtext                              null comment '参赛/获奖证明图片（base64数据）',
    auto_review_status   varchar(32) default 'PENDING'         not null comment '自动审核状态：PENDING-待审核, PASSED-通过, FAILED-失败',
    auto_review_comment  varchar(1024)                         null comment 'AI自动审核分析意见',
    admin_review_status  varchar(32) default 'PENDING'         not null comment '管理员审核状态：PENDING-待审核, PASSED-通过, FAILED-失败',
    admin_review_comment varchar(1024)                         null comment '管理员审核意见',
    admin_id             bigint                                null comment '审核管理员ID',
    admin_review_time    datetime                              null comment '管理员审核时间',
    create_time          datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time          datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete            tinyint     default 0                 not null comment '是否删除',
    INDEX idx_user_id (user_id),
    INDEX idx_category_id (category_id),
    INDEX idx_activity_type_id (activity_type_id),
    INDEX idx_auto_review_status (auto_review_status),
    INDEX idx_admin_review_status (admin_review_status)
) comment '比赛记录（教师竞赛获奖台账）' collate = utf8mb4_unicode_ci;

-- ===================== 教师个人得分明细表 =====================
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
    FOREIGN KEY fk_record(record_id) REFERENCES competition_record(id),
    FOREIGN KEY fk_user(user_id) REFERENCES user(id),
    INDEX idx_record_id(record_id),
    INDEX idx_user_id(user_id)
) COMMENT '教师单场竞赛个人得分明细' COLLATE utf8mb4_unicode_ci;
