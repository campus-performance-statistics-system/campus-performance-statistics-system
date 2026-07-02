
# 校园绩效统计系统开发规范指南
## 项目基本信息
| 项 | 说明 |
|----|------|
| 项目名称 | campus-performance-statistics-system（校园绩效统计系统） |
| 工作目录 | `D:\code\campus-performance-statistics-system` |
| 代码作者 | 15180 |
| 运行环境 | Windows 11，JDK 21.0.8（项目编译指定Java 17，兼容JDK 21运行） |
| 构建工具 | Maven 3.x |
| 当前时间 | 2026-07-02 17:39:32 |

---

## 一、技术栈要求
### 核心框架与版本
- **主框架**：Spring Boot 3.5.14
- **编译语言版本**：Java 17
- **ORM框架**：MyBatis-Flex 1.11.1
- **数据库连接池**：HikariCP 4.0.3
- **数据库**：MySQL 8.x（驱动版本由Spring Boot父工程管理）
- **缓存/会话**：Redis + Redisson 3.52.0 + Spring Session
- **接口文档**：Knife4j 4.4.0（基于SpringDoc OpenAPI 3）
- **工具库**：Lombok 1.18.36、Hutool 5.8.43、Apache POI 5.4.0
- **AI能力**：Spring AI Alibaba 1.0.0.2 + 阿里云DashScope SDK 2.22.4
- **其他依赖**：Spring AOP、Jedis

### 核心依赖约束
1. 所有依赖版本优先由Spring Boot父工程统一管理，特殊版本需在`pom.xml`的`dependencyManagement`中声明
2. 禁止随意升级核心依赖版本，需升级时需评估兼容性并同步更新规范

---

## 二、项目目录结构
```markdown
campus-performance-statistics-system
├── .claude              # Claude配置目录
├── docs                 # 项目文档目录（需求、设计、接口文档等）
├── frontend             # 前端项目目录
│   ├── public           # 前端静态资源
│   └── src
│       ├── api          # 前端接口封装
│       ├── assets       # 前端静态资源（图片、样式等）
│       ├── components   # 公共组件
│       ├── layouts      # 布局组件
│       ├── pages        # 页面组件
│       │   ├── admin    # 后台管理页
│       │   ├── competition # 竞赛相关页
│       │   ├── rank     # 排名相关页
│       │   └── user     # 用户相关页
│       ├── router       # 前端路由配置
│       ├── stores       # 前端状态管理
│       └── utils        # 前端工具类
├── sql                  # 数据库脚本目录（初始化脚本、升级脚本）
├── src
│   ├── main
│   │   ├── java
│   │   │   └── com.jgh.ghairouter # 后端主包
│   │   │       ├── annotation     # 自定义注解（权限、日志、校验等）
│   │   │       ├── aop            # AOP切面（日志、权限、事务增强等）
│   │   │       ├── common         # 通用类（响应结果、分页、通用工具类）
│   │   │       ├── config         # 配置类（Redis、MyBatis-Flex、AI、Knife4j等）
│   │   │       ├── constant       # 常量类（错误码、业务常量等）
│   │   │       ├── controller     # 接口层（处理HTTP请求/响应）
│   │   │       ├── exception      # 异常类、全局异常处理器
│   │   │       ├── mapper         # MyBatis-Flex数据访问层
│   │   │       ├── model          # 数据模型层
│   │   │       │   ├── constants  # 模型相关常量
│   │   │       │   ├── dto        # 数据传输对象（层间传输用）
│   │   │       │   ├── entity     # 数据库实体对象（DO，映射数据库表）
│   │   │       │   ├── enums      # 业务枚举
│   │   │       │   └── vo         # 视图对象（返回前端用）
│   │   │       ├── service        # 业务接口层
│   │   │       │   └── impl       # 业务接口实现类
│   │   │       └── utils          # 后端工具类
│   │   └── resources              # 资源文件目录
│   │       └── application.yml    # 全局配置文件
│   └── test                       # 测试目录
├── uploads                         # 文件上传存储目录（禁止提交到版本库）
```

---

## 三、分层架构规范
| 层级 | 职责说明 | 开发约束与注意事项 |
|------|----------|--------------------|
| **Controller** | 处理HTTP请求与响应，定义API接口 | 1. 不得直接访问数据库，必须通过Service层调用<br>2. 入参必须加`@Valid`做JSR-303校验<br>3. 返回结果统一用`common`包下的通用响应对象，禁止直接返回Entity |
| **Service** | 实现业务逻辑、事务管理与数据校验 | 1. 必须通过Mapper层访问数据库<br>2. 返回DTO/VO而非Entity（除非必要）<br>3. `@Transactional`注解仅用于Service层方法，禁止在循环中频繁提交事务 |
| **Mapper** | 数据库访问与持久化操作 | 1. 继承MyBatis-Flex的`BaseMapper`接口<br>2. 优先使用MyBatis-Flex提供的`QueryWrapper`做查询，禁止手动拼接SQL字符串，防止SQL注入<br>3. 复杂查询使用`@EntityGraph`避免N+1查询问题 |
| **Model层** | 数据模型封装 | 1. `entity`包下的类为数据库实体（DO），必须加`@Table`注解映射对应表，字段遵循驼峰命名（MyBatis-Flex已开启下划线转驼峰配置）<br>2. `dto`包为数据传输对象，用于Controller和Service之间传参<br>3. `vo`包为视图对象，用于返回前端数据，禁止返回Entity<br>4. `enums`包存放业务枚举，禁止在代码中使用魔法值 |

### 接口与实现分离规范
1. 所有业务逻辑必须通过接口定义（如`UserService`），具体实现类放在接口所在包下的`impl`子包中（如`UserServiceImpl`）
2. 自定义注解、AOP切面、配置类、常量类、异常类等通用组件统一放在对应包下，禁止散落在业务包中

---

## 四、安全与性能规范
### 输入校验规范
1. 使用`jakarta.validation.constraints`下的校验注解（如`@NotBlank`、`@Size`、`@Min`等），禁止手动做参数校验
2. 所有用户输入必须做合法性校验，防止XSS、SQL注入等安全漏洞
3. 禁止在代码中硬编码敏感信息（如数据库密码、AI接口密钥、Redis密码等），统一放在配置中心或环境变量中

### 缓存与会话规范
1. 用户Session统一存储在Redis中，过期时间30天，Redis键统一加项目前缀`cps:`（campus-performance-statistics-system缩写），避免多项目冲突
2. 分布式锁、分布式缓存必须使用Redisson实现，禁止直接操作Redis原生API
3. 禁止在缓存中存储敏感信息，缓存数据必须做脱敏处理

### 文件上传规范
1. 上传文件统一存储在项目根目录的`uploads`文件夹下，禁止存储在其他路径
2. 单文件上传大小限制10MB，总请求大小限制10MB，上传前必须校验文件类型、大小，防止恶意文件上传
3. 上传的文件名必须做重命名处理，避免文件名冲突和路径遍历漏洞

### AI能力调用规范
1. 所有AI调用必须通过后端统一封装，禁止前端直接调用阿里云DashScope接口，防止API Key泄露
2. AI调用必须做熔断、降级、限流处理，避免大模型接口故障影响主业务
3. AI生成的内容必须做敏感词过滤，避免输出违规内容

### 事务与性能规范
1. `@Transactional`注解仅用于Service层方法，事务传播机制默认使用`REQUIRED`
2. 避免在事务方法中做RPC调用、IO操作，防止事务超时
3. 大数据量查询必须做分页，禁止一次性查询全量数据
4. 热点数据必须做缓存，缓存过期时间根据业务场景设置，避免缓存击穿、穿透、雪崩

---

## 五、代码风格规范
### 命名规范
| 类型 | 命名方式 | 示例 |
|------|----------|------|
| 类名 | UpperCamelCase | `UserServiceImpl`、`CompetitionQueryWrapper` |
| 方法/变量 | lowerCamelCase | `saveUser()`、`competitionList` |
| 常量 | UPPER_SNAKE_CASE | `MAX_LOGIN_ATTEMPTS`、`USER_SESSION_KEY` |
| 包名 | 全小写，单词间用`.`分隔 | `com.jgh.ghairouter.service.impl` |
| 数据库表名 | 小写+下划线，复数形式 | `user`、`competition_record` |
| 数据库字段名 | 小写+下划线 | `user_name`、`create_time` |

### 类型后缀规范（阿里巴巴风格）
| 后缀 | 用途说明 | 存放包 | 示例 |
|------|----------|--------|------|
| DTO | 数据传输对象，用于层间传参 | `model.dto` | `UserLoginDTO`、`CompetitionAddDTO` |
| DO | 数据库实体对象，映射数据库表 | `model.entity` | `UserDO`、`CompetitionDO` |
| BO | 业务逻辑封装对象，用于复杂业务逻辑传参 | `model.dto` | `CompetitionStatBO` |
| VO | 视图展示对象，用于返回前端数据 | `model.vo` | `UserInfoVO`、`RankListVO` |
| Query | 查询参数封装对象 | `model.dto` | `UserQuery`、`CompetitionQuery` |

### 注释规范
1. 所有类、方法、字段必须添加**Javadoc注释**，注释使用中文（项目第一语言）
2. 类注释需说明类的作用、作者、创建时间
3. 方法注释需说明方法功能、参数说明、返回值说明、异常说明
4. 复杂业务逻辑必须添加行内注释，说明逻辑用途
5. 禁止使用无意义的注释（如`// 修改时间`、`// 注释掉`等）

### 实体类规范
1. 实体类必须使用Lombok注解简化代码，统一使用`@Data`、`@NoArgsConstructor`、`@AllArgsConstructor`
2. 实体类主键字段加`@Id`注解，自增主键加`@AutoFill`注解（如果需要自动填充）
3. 实体类禁止添加业务逻辑方法，仅作为数据载体

---

## 六、扩展性与通用规范
### 接口文档规范
1. 所有Controller接口必须添加Knife4j注解：`@Tag`标注接口分组，`@Operation`标注接口功能，`@Parameter`标注参数说明
2. 接口路径需遵循RESTful风格，动词使用小写，复数形式，如`/api/competition/list`、`/api/user/info`
3. 接口版本统一在路径中体现，如`/api/v1/competition/list`，避免后续接口升级影响旧版本

### 日志规范
1. 禁止使用`System.out.println`输出日志，统一使用Lombok的`@Slf4j`注解
2. 日志级别遵循规范：`error`记录错误异常、`warn`记录警告信息、`info`记录关键业务节点、`debug`记录调试信息（生产环境关闭debug日志）
3. 日志输出必须包含链路ID、用户ID等上下文信息，方便问题排查
4. 禁止在日志中输出敏感信息（如密码、身份证号、手机号等）

### 通用规范
1. 遵循SOLID、DRY、KISS、YAGNI编码原则，避免过度设计
2. 遵循OWASP安全规范，防范SQL注入、XSS、CSRF等常见安全漏洞
3. 统一异常处理：自定义业务异常继承`RuntimeException`，通过`@RestControllerAdvice`做全局异常处理，统一返回错误码和提示信息
4. 分页参数统一使用`common`包下的`PageParam`和`PageResult`，禁止自定义分页参数
5. 响应结果统一使用`common`包下的`Result`对象，格式为`{code: 200, msg: "success", data: {}}`

---

## 七、项目特定配置规范
1. 服务端口：`6789`，上下文路径：`/api`，所有接口需带`/api`前缀
2. 默认激活环境：`local`，多环境配置统一放在`application-{env}.yml`中
3. MyBatis-Flex已开启下划线转驼峰映射，Entity字段和数据库字段无需严格对应
4. 项目包扫描基础路径：`com.jgh.ghairouter`，新增包必须放在该路径下
5. `uploads`目录为文件上传存储目录，需在`.gitignore`中忽略，禁止提交到版本库，定期清理过期文件
6. `sql`目录存放数据库脚本，脚本命名遵循`V{版本号}__{描述}.sql`格式，如`V1__init_user_table.sql`
```

