-- 创建库
create database if not exists campus_performance_statistics_system;

-- 切换库
use campus_performance_statistics_system;

-- ===================== 原有用户表 =====================
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
                                                                                                  (2, 'user', '10670d38ec32fa8102be6a37f8cb52bf', '普通用户','', '我是一个普通用户', 'user');

-- ===================== 原有比赛分类、活动类型 =====================
-- 比赛分类表（仅顶层分类：服务类、个人业务类）
create table if not exists category
(
    id          bigint auto_increment comment 'id' primary key,
    name        varchar(256)                          not null comment '分类名称',
    description varchar(512)                          null comment '分类描述',
    sort_order  int         default 0                 not null comment '排序',
    create_time datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete   tinyint     default 0                 not null comment '是否删除',
    INDEX idx_sort_order (sort_order)
) comment '比赛分类' collate = utf8mb4_unicode_ci;

-- 预置分类数据（仅两个顶层分类）
INSERT INTO category (id, name, description, sort_order) VALUES
                                                             (1, '服务类', '学校组织的各类服务性比赛与活动', 1),
                                                             (2, '个人业务类', '个人参加的竞赛类项目', 2);

-- 活动类型表（具体比赛/活动类型，隶属于分类）
create table if not exists activity_type
(
    id                  bigint auto_increment comment 'id' primary key,
    name                varchar(256)                          not null comment '活动类型名称',
    description         varchar(512)                          null comment '活动类型描述',
    category_id         bigint                                not null comment '所属分类ID',
    competition_rank_id bigint                                null comment '关联竞赛等级ID',
    sponsor_unit        varchar(512)                          null comment '颁奖/主办单位',
    sort_order          int         default 0                 not null comment '排序',
    create_time         datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time         datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete           tinyint     default 0                 not null comment '是否删除',
    INDEX idx_category_id (category_id),
    INDEX idx_competition_rank_id (competition_rank_id),
    INDEX idx_sort_order (sort_order)
) comment '活动类型' collate = utf8mb4_unicode_ci;

-- 预置活动类型数据
INSERT INTO activity_type (id, name, description, category_id, competition_rank_id, sponsor_unit, sort_order) VALUES
-- 服务类下的具体活动类型
(1, '学校文体比赛', '学校组织的文艺、体育类比赛活动', 1, 4, '校团委/学工处', 1),
(2, '志愿服务活动', '校内外志愿服务活动记录', 1, 4, '校团委/青年志愿者协会', 2),
(3, '学生社团活动', '学生社团组织的各类活动', 1, 4, '校团委/社团联合会', 3),
-- 个人业务类下的具体活动类型
(4, '大学生创新创业训练计划', '大创项目申报与结题', 2, 1, '教育部/学校教务处', 4),
(5, '学科竞赛', '各类学科竞赛（教学创新、信息化、数字创意、课程思政等）', 2, 1, '教育部/各学科教指委', 5),
(6, '技能证书', '各类专业技能证书考取', 2, 1, '相关行业认证机构', 6);

-- ===================== 新增1：竞赛等级表 competition_rank =====================
CREATE TABLE IF NOT EXISTS competition_rank
(
    id          BIGINT AUTO_INCREMENT COMMENT '主键ID' PRIMARY KEY,
    rank_name   VARCHAR(64) NOT NULL COMMENT '竞赛层级名称：校级/区级/自治区级行业性/国家级',
    rank_code   VARCHAR(32) NOT NULL COMMENT '层级编码（方便程序判断）',
    sort_order  INT DEFAULT 0 NOT NULL COMMENT '排序：国家级1>自治区2>区级3>校级4',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    is_delete   TINYINT DEFAULT 0 NOT NULL,
    UNIQUE KEY uk_rank_code(rank_code),
    UNIQUE KEY uk_rank_name(rank_name)
) COMMENT '竞赛等级（赛事主办层级）' COLLATE = utf8mb4_unicode_ci;

-- 预置层级数据（按表格备注提取）
INSERT INTO competition_rank(rank_name, rank_code, sort_order) VALUES
                                                                   ('国家级', 'NATIONAL', 1),
                                                                   ('自治区级行业性', 'AUTONOMY_INDUSTRY', 2),
                                                                   ('区级', 'REGIONAL', 3),
                                                                   ('校级', 'SCHOOL', 4);

-- ===================== 新增2：获奖等级表 award_grade =====================
CREATE TABLE IF NOT EXISTS award_grade
(
    id          BIGINT AUTO_INCREMENT COMMENT '主键ID' PRIMARY KEY,
    grade_name  VARCHAR(64) NOT NULL COMMENT '获奖等级：一等奖/二等奖/三等奖/优秀奖/未获奖',
    grade_code  VARCHAR(32) NOT NULL COMMENT '等级编码',
    sort_order  INT DEFAULT 0 NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    is_delete   TINYINT DEFAULT 0 NOT NULL,
    UNIQUE KEY uk_grade_code(grade_code),
    UNIQUE KEY uk_grade_name(grade_name)
) COMMENT '获奖等级' COLLATE = utf8mb4_unicode_ci;

-- 预置获奖等级
INSERT INTO award_grade(grade_name, grade_code, sort_order) VALUES
                                                                ('一等奖', 'FIRST', 1),
                                                                ('二等奖', 'SECOND', 2),
                                                                ('三等奖', 'THIRD', 3),
                                                                ('优秀奖', 'EXCELLENT', 4),
                                                                ('未获奖', 'NONE', 5);

-- ===================== 新增3：竞赛等级-获奖等级 多对多计分关联表 rank_grade_score =====================
-- 核心规则来自表格备注：
-- 校级：参与2分/项（负责人）
-- 区级：一等2分、其他1.5、优秀奖1分
-- 自治区行业：一等7分
-- 国家级：一等15、二等12、三等10分
CREATE TABLE IF NOT EXISTS rank_grade_score
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    rank_id         BIGINT NOT NULL COMMENT '竞赛等级ID，关联competition_rank',
    grade_id        BIGINT NOT NULL COMMENT '获奖等级ID，关联award_grade',
    base_score      DECIMAL(5,2) NOT NULL COMMENT '该组合基础总分',
    remark          VARCHAR(512) NULL COMMENT '计分备注说明',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    is_delete       TINYINT DEFAULT 0 NOT NULL,
    -- 联合唯一约束：同一个层级+奖项只能一条计分规则
    UNIQUE KEY uk_rank_grade(rank_id, grade_id),
    -- 外键关联
    FOREIGN KEY fk_rank(rank_id) REFERENCES competition_rank(id),
    FOREIGN KEY fk_grade(grade_id) REFERENCES award_grade(id),
    INDEX idx_rank_id(rank_id),
    INDEX idx_grade_id(grade_id)
) COMMENT '竞赛层级-获奖等级计分规则（多对多中间表）' COLLATE = utf8mb4_unicode_ci;

-- 批量插入计分规则（完全匹配表格备注）
INSERT INTO rank_grade_score(rank_id, grade_id, base_score, remark) VALUES
-- 1. 国家级
((SELECT id FROM competition_rank WHERE rank_code='NATIONAL'), (SELECT id FROM award_grade WHERE grade_code='FIRST'), 15.00, '国家级一等奖15分'),
((SELECT id FROM competition_rank WHERE rank_code='NATIONAL'), (SELECT id FROM award_grade WHERE grade_code='SECOND'), 12.00, '国家级二等奖12分'),
((SELECT id FROM competition_rank WHERE rank_code='NATIONAL'), (SELECT id FROM award_grade WHERE grade_code='THIRD'), 10.00, '国家级三等奖10分'),
-- 2. 自治区级行业性
((SELECT id FROM competition_rank WHERE rank_code='AUTONOMY_INDUSTRY'), (SELECT id FROM award_grade WHERE grade_code='FIRST'), 7.00, '自治区级行业性一等奖7分'),
-- 3. 区级
((SELECT id FROM competition_rank WHERE rank_code='REGIONAL'), (SELECT id FROM award_grade WHERE grade_code='FIRST'), 2.00, '区级一等奖2分'),
((SELECT id FROM competition_rank WHERE rank_code='REGIONAL'), (SELECT id FROM award_grade WHERE grade_code='SECOND'), 1.50, '区级二等奖1.5分'),
((SELECT id FROM competition_rank WHERE rank_code='REGIONAL'), (SELECT id FROM award_grade WHERE grade_code='THIRD'), 1.50, '区级三等奖1.5分'),
((SELECT id FROM competition_rank WHERE rank_code='REGIONAL'), (SELECT id FROM award_grade WHERE grade_code='EXCELLENT'), 1.00, '区级优秀奖1分'),
-- 4. 校级（无论奖项，参与即2分）
((SELECT id FROM competition_rank WHERE rank_code='SCHOOL'), (SELECT id FROM award_grade WHERE grade_code='FIRST'), 2.00, '校级参与2分（负责人）'),
((SELECT id FROM competition_rank WHERE rank_code='SCHOOL'), (SELECT id FROM award_grade WHERE grade_code='SECOND'), 2.00, '校级参与2分（负责人）'),
((SELECT id FROM competition_rank WHERE rank_code='SCHOOL'), (SELECT id FROM award_grade WHERE grade_code='THIRD'), 2.00, '校级参与2分（负责人）'),
((SELECT id FROM competition_rank WHERE rank_code='SCHOOL'), (SELECT id FROM award_grade WHERE grade_code='NONE'), 2.00, '校级未获奖参与2分');

-- ===================== 新增4：团队分数分配规则表 score_distribute_rule =====================
-- 规则来源表格底部备注：
-- 两人完成：7:3；三人完成：6:2:2；四人及以上：主持人50%，剩余平均分50%
CREATE TABLE IF NOT EXISTS score_distribute_rule
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    member_count    INT NOT NULL COMMENT '团队总人数',
    rule_desc       VARCHAR(128) NOT NULL COMMENT '分配规则文字描述',
    leader_ratio    DECIMAL(3,2) NOT NULL COMMENT '负责人/主持人占比',
    member_ratio    DECIMAL(3,2) NOT NULL COMMENT '普通成员人均占比',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    is_delete       TINYINT DEFAULT 0 NOT NULL,
    UNIQUE KEY uk_member_count(member_count)
) COMMENT '团队总分拆分比例规则' COLLATE = utf8mb4_unicode_ci;

-- 预置分配规则
INSERT INTO score_distribute_rule(member_count, rule_desc, leader_ratio, member_ratio) VALUES
                                                                                           (2, '两人完成，负责人70%，另一人30%', 0.70, 0.30),
                                                                                           (3, '三人完成，负责人60%，其余两人各20%', 0.60, 0.20),
                                                                                           (4, '四人及以上，主持人50%，剩余所有人平分50%', 0.50, 0.50);

-- ===================== 改造原有比赛记录表 competition_record =====================
-- 替换原字符串award_level为外键，新增竞赛等级、团队人数、分配规则关联字段
DROP TABLE IF EXISTS competition_record;
create table if not exists competition_record
(
    id                   bigint auto_increment comment 'id' primary key,
    user_id              bigint                                not null comment '填报用户ID',
    category_id          bigint                                not null comment '比赛大类ID',
    activity_type_id     bigint                                null comment '活动细分类型ID',
    competition_rank_id bigint                                not null comment '竞赛层级ID（关联competition_rank）',
    award_grade_id      bigint                                not null comment '获奖等级ID（关联award_grade）',
    competition_name     varchar(256)                          not null comment '比赛全称',
    sponsor_unit         varchar(512)                          null comment '颁奖/主办单位',
    team_member_num      int                                   not null default 1 comment '参赛总人数（1=单人参赛）',
    distribute_rule_id   bigint                                null comment '分数分配规则ID（多人团队用）',
    first_author_id      bigint                                null comment '第一负责人用户ID（关联user）',
    other_author_ids     varchar(1024)                         null comment '其他参赛教师ID，逗号分隔',
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
    -- 索引
    INDEX idx_user_id (user_id),
    INDEX idx_category_id (category_id),
    INDEX idx_activity_type_id (activity_type_id),
    INDEX idx_competition_rank_id (competition_rank_id),
    INDEX idx_award_grade_id (award_grade_id),
    INDEX idx_distribute_rule_id (distribute_rule_id),
    INDEX idx_auto_review_status (auto_review_status),
    INDEX idx_admin_review_status (admin_review_status),
    -- 外键约束
    FOREIGN KEY fk_record_rank(competition_rank_id) REFERENCES competition_rank(id),
    FOREIGN KEY fk_record_grade(award_grade_id) REFERENCES award_grade(id),
    FOREIGN KEY fk_record_distribute(distribute_rule_id) REFERENCES score_distribute_rule(id)
) comment '比赛记录（教师竞赛获奖台账）' collate = utf8mb4_unicode_ci;

-- ===================== 可选扩展：教师个人得分明细表（按需新增） =====================
-- 用于存储每条竞赛拆分后每个老师的最终得分，避免实时计算
CREATE TABLE IF NOT EXISTS teacher_competition_score
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id       BIGINT NOT NULL COMMENT '关联比赛记录ID',
    teacher_user_id BIGINT NOT NULL COMMENT '教师用户ID',
    personal_score  DECIMAL(6,3) NOT NULL COMMENT '本次竞赛该教师个人得分',
    is_leader       TINYINT DEFAULT 0 NOT NULL COMMENT '是否负责人：1是0否',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    is_delete       TINYINT DEFAULT 0 NOT NULL,
    UNIQUE KEY uk_record_teacher(record_id, teacher_user_id),
    FOREIGN KEY fk_record(record_id) REFERENCES competition_record(id),
    FOREIGN KEY fk_teacher(teacher_user_id) REFERENCES user(id),
    INDEX idx_record_id(record_id),
    INDEX idx_teacher_user_id(teacher_user_id)
) COMMENT '教师单场竞赛个人得分明细' COLLATE utf8mb4_unicode_ci;

ALTER TABLE activity_type
    ADD COLUMN competition_rank_id BIGINT NULL COMMENT '关联竞赛等级ID' AFTER category_id,
    ADD INDEX idx_competition_rank_id (competition_rank_id);

-- Set default ranks for existing activity types
UPDATE activity_type SET competition_rank_id = 4 WHERE id IN (1, 2, 3);  -- 服务类 → 校级
UPDATE activity_type SET competition_rank_id = 1 WHERE id IN (4, 5, 6);  -- 个人业务类 → 国家级
