-- 创建库
create database if not exists campus_performance_statistics_system;

-- 切换库
use campus_performance_statistics_system;

-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName)
    ) comment '用户' collate = utf8mb4_unicode_ci;

-- 密码是 12345678(MD5 加密 + 盐值 yupi)
INSERT INTO user (id, userAccount, userPassword, userName, userAvatar, userProfile, userRole) VALUES
(1, 'admin', '10670d38ec32fa8102be6a37f8cb52bf', '管理员', '', '系统管理员', 'admin'),
(2, 'user', '10670d38ec32fa8102be6a37f8cb52bf', '普通用户','' , '我是一个普通用户', 'user');

-- 比赛分类表
create table if not exists category
(
    id          bigint auto_increment comment 'id' primary key,
    name        varchar(256)                          not null comment '分类名称',
    description varchar(512)                          null comment '分类描述',
    parent_id   bigint      default 0                 not null comment '父分类ID，0表示顶层分类',
    sort_order  int         default 0                 not null comment '排序',
    create_time datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete   tinyint     default 0                 not null comment '是否删除',
    INDEX idx_parent_id (parent_id),
    INDEX idx_sort_order (sort_order)
) comment '比赛分类' collate = utf8mb4_unicode_ci;

-- 比赛记录表
create table if not exists competition_record
(
    id                   bigint auto_increment comment 'id' primary key,
    user_id              bigint                                not null comment '参赛用户ID',
    category_id          bigint                                not null comment '分类ID',
    competition_name     varchar(256)                          not null comment '比赛名称',
    award_level          varchar(64)                           not null comment '获奖等级',
    first_author         varchar(256)                          not null comment '第一作者',
    other_authors        varchar(1024)                         null comment '其他作者（逗号分隔）',
    proof_image_data     longtext                              not null comment '参赛/获奖证明图片（base64数据）',
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
    INDEX idx_auto_review_status (auto_review_status),
    INDEX idx_admin_review_status (admin_review_status)
) comment '比赛记录' collate = utf8mb4_unicode_ci;

-- 预置分类数据（两级：顶层 → 子分类）
INSERT INTO category (id, name, description, parent_id, sort_order) VALUES
-- 顶层分类
(1, '服务类', '学校组织的各类服务性比赛与活动', 0, 1),
(2, '个人业务类', '个人参加的竞赛类项目', 0, 2),
-- 服务类子分类
(3, '学校文体比赛', '学校组织的文艺、体育类比赛活动', 1, 1),
(4, '志愿服务活动', '校内外志愿服务活动记录', 1, 2),
(5, '学生社团活动', '学生社团组织的各类活动', 1, 3),
-- 个人业务类子分类
(6, '大学生创新创业训练计划', '大创项目申报与结题', 2, 1),
(7, '学科竞赛', '各类学科竞赛（数学建模、程序设计等）', 2, 2),
(8, '技能证书', '各类专业技能证书考取', 2, 3);
