# 首页推荐系统与推荐算法说明文档

本文档详细阐述 Y 社区（Social Platform）首页推荐流的**核心业务逻辑、底层数据流转、用户兴趣画像建模、Elasticsearch 高斯衰减推荐算法**以及**游标分页与保底策略**。

---

## 1. 总体推荐架构与链路设计

首页推荐接口为 `GET /post/list?lastId={lastId}&offset={offset}`。系统根据用户的登录状态与交互历史，采取分层推荐策略：

```
                              用户请求 GET /post/list
                                         │
                                   是否携带有效 Token?
                                    /           \
                                 [否]           [是]
                                  │               │
                                  │        查询用户兴趣画像
                                  │      (Redis Hash / DB)
                                  │               │
                                  │        兴趣画像是否为空?
                                  │         /           \
                                  │       [是]          [否]
                                  │        │             │
                                  ▼        ▼             ▼
                     ┌────────────────────────┐  ┌─────────────────────────┐
                     │ 全局时间流保底推荐      │  │ 个性化加权推荐          │
                     │ (Redis ZSet / DB 降级) │  │ (ES Function Score 查询)│
                     └────────────────────────┘  └─────────────────────────┘
                                  │                          │
                                  └────────────┬─────────────┘
                                               │
                                      聚合点赞状态与点赞数
                                       (DataCacheUtil 批量)
                                               │
                                      包装为 ScrollResult<PostVO>
                                               │
                                           返回前端
```

### 1.1 两种推荐模式对比

| 推荐模式 | 适用人群 | 数据引擎 | 核心排序指标 | 分页机制 |
|---|---|---|---|---|
| **个性化兴趣推荐** | 已登录且有历史交互行为的用户 | **Elasticsearch** (Function Score) | 兴趣分类 Boost 权重 $\times$ 发布时间高斯衰减得分 | 基于综合得分与偏移量的分页 |
| **全局时间流保底** | 未登录访客 / 无行为记录的新用户 / 冷启动 | **Redis** (ZSet) + **MySQL** (`home_post`) | 发帖时间戳倒序（最新优先） | 基于时间戳与同分计数的高精度游标分页 |

---

## 2. 用户兴趣画像模型（Interest Profile Model）

### 2.1 隐式反馈行为与权重矩阵

系统不依赖用户显式选择标签，而是通过用户在平台上的**隐式交互行为（Implicit Feedback）**，动态捕捉并累加用户对各内容分类（`categoryId`）的兴趣偏好分数：

| 用户行为 | 触发接口 / 业务位置 | 兴趣分变动 ($\Delta \text{score}$) | 业务含义说明 |
|---|---|---|---|
| **浏览帖子详情** | `GET /post/{id}` (`PostServiceImpl.getPost`) | **+1 分** | 发生点击与阅读，产生初步兴趣 |
| **发表评论** | `POST /comment` (`CommentServiceImpl.addComment`) | **+5 分** | 发生深度互动与观点表达，兴趣度较高 |
| **点赞帖子** | `POST /like/{postId}` (`LikeServiceImpl.like`) | **+10 分** | 强正向反馈，高度认同该分类内容 |
| **取消点赞** | `POST /like/{postId}` (`LikeServiceImpl.like`) | **-10 分** | 正向反馈撤销，同步扣减对应分类得分 |

### 2.2 兴趣分读写与持久化架构

```
用户交互 (点赞/评论/浏览)
       │
       ▼
UserIntersetScoreUtil.changeScore()
       │
       ├── 1. Redis 原子累加: HINCRBY sp:user:interest:{userId} {categoryId} {score}
       ├── 2. 刷新缓存 TTL: EXPIRE 7 DAYS
       │
       └── 3. 异步持久化: @Async AsyncTaskUtil.asyncSaveUserInterestScore()
                               │
                               ▼
                    UPSERT INTO user_interest_score
```

- **高并发低延迟**：通过 Redis Hash 数据结构（`Key: sp:user:interest:{userId}`）进行 `HINCRBY` 原子操作，避免并发冲突，读写耗时在亚毫秒级。
- **异步落盘削峰**：利用 Spring 线程池 `@Async("mvcTaskExecutor")` 执行 MySQL `user_interest_score` 表的插入/更新操作，不阻塞用户核心操作线程。
- **缓存穿透与自愈**：当 Redis 缓存过期或被逐出时，`getUserInterestScores()` 会自动回源查询 MySQL，将用户的全部分类得分重新加载至 Redis Hash 并赋予 7 天 TTL。

---

## 3. Elasticsearch 个性化推荐算法详解

当系统检测到用户存在非空兴趣画像时，触发 Elasticsearch 的 **Function Score Query** 复合评分检索。

### 3.1 核心评分函数与数学模型

个性化推荐的最终得分由 **分类兴趣相关性得分** 与 **时间高斯衰减因子** 相乘得出：

$$\text{FinalScore} = \text{Score}_{\text{Category Match}} \times \text{Decay}(\text{createTime})$$

#### 3.1.1 兴趣加权分（Category Boosting）

设用户当前对各分类的兴趣得分集合为 $\{ (c_1, s_1), (c_2, s_2), \dots, (c_n, s_n) \}$，其中 $s_i > 0$。

1. **计算归一化基准**：
   $$S_{\max} = \max(s_1, s_2, \dots, s_n, 1)$$
2. **计算分类相对权重 Boost**：
   $$\text{boost}(c_i) = \frac{s_i}{S_{\max}} \in (0, 1]$$
3. **Bool 查询组合**：
   - `must`: `term(enabled: true)` —— 严格过滤未上架/已被违规封禁的帖子；
   - `should`: 为每个有得分的分类生成子句 `term(categoryId: c_i).boost(boost(c_i))`；
   - `minimum_should_match: 0` —— 保证无兴趣偏好分类的内容仍有一定概率被检索出来，防止陷入极端的“信息茧房”。

#### 3.1.2 时间高斯衰减函数（Gaussian Recency Decay）

为了平衡“用户兴趣”与“内容时效性”，系统对帖子的发布时间（`createTime`）施加高斯衰减函数（Gauss Function）：

```
衰减分 Decay
 1.0 ┼───────────────────────╮
     │                       │
 0.5 ┼ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┼ ─ ─ ─ ─ ─ ╮ (Scale = 7 天, Decay = 0.5)
     │                       │           │
 0.0 ┴───────────────────────┴───────────┴───────────► 距离发布的时间差 (天)
   origin (now)             7天         14天
```

- **原点（origin）**：`now`（当前请求时间戳）
- **尺度（scale）**：`7d`（7 天）
- **衰减系数（decay）**：`0.5`（发布距今 7 天的内容，时间因子得分为 0.5）
- **高斯衰减数学公式**：
  $$\text{Decay}(t) = \exp \left( -\frac{\max(0, |\text{now} - t|)^2}{2\sigma^2} \right)$$
  其中 $\sigma = \frac{\text{scale}}{\sqrt{-2 \ln(\text{decay})}} = \frac{7}{\sqrt{2 \ln 2}} \approx 5.945\text{ 天}$。

#### 3.1.3 复合增强模式（Boost Mode）
采用 `FunctionBoostMode.Multiply`，即：
$$\text{Score} = \text{QueryScore} \times \text{DecayScore}$$
- 偏好分类的最新发布帖子将获得最高推荐位；
- 虽符合用户兴趣但时间久远的旧帖子将被平滑降权；
- 最新发布的其他分类高质量帖子依然能获得合理展示机会。

### 3.2 ES Java DSL 查询构建核心代码

```java
int maxScore = interestScores.values().stream().mapToInt(Integer::intValue).max().orElse(1);

NativeQuery nativeQuery = NativeQuery.builder()
    .withQuery(Query.of(q -> q.functionScore(fs -> {
        // 1. 基础布尔查询与分类权重加成
        fs.query(fq -> fq.bool(b -> {
            b.must(m -> m.term(t -> t.field("enabled").value(true)));
            for (Map.Entry<Integer, Integer> entry : interestScores.entrySet()) {
                if (entry.getValue() > 0) {
                    float boost = (float) entry.getValue() / maxScore;
                    b.should(s -> s.term(t -> t.field("categoryId").value(entry.getKey()).boost(boost)));
                }
            }
            b.minimumShouldMatch("0");
            return b;
        }));
        // 2. 时间高斯衰减函数
        fs.functions(f -> f.gauss(g -> g
            .date(d -> d
                .field("createTime")
                .placement(p -> p
                    .origin("now")
                    .scale(Time.of(t -> t.time("7d")))
                    .decay(0.5)
                )
            )
        ));
        // 3. 乘法合成最终得分
        fs.boostMode(FunctionBoostMode.Multiply);
        return fs;
    })))
    .withSort(Sort.by(Sort.Order.desc("_score")))
    .withPageable(PageRequest.of(offset / pageSize, pageSize))
    .build();

SearchHits<Post> searchHits = elasticsearchTemplate.search(nativeQuery, Post.class);
```

---

## 4. 全局时间流保底机制（Fallback & Cold Start）

在未登录、新用户画像为空或 ES 暂时不可用时，系统平滑回退到全局时间流推荐机制。

```
发帖发布 (publishPost)
     │
     ├── 1. 插入 post 表 (MySQL)
     ├── 2. 插入 home_post 表 (MySQL)
     ├── 3. 写入 Redis 首页 ZSet: sp:home:post:list (Score = createTime 毫秒)
     ├── 4. 写入 ES 帖子索引: postIndex (IK 中文分词)
     └── 5. 写入粉丝收件箱 (推模式 / Feed 流)
```

### 4.1 核心组件与数据结构
1. **`home_post` 数据表**：独立维护首页候选帖子清单（`id, post_id, user_id, create_time`），支持运营管理与冷数据维护。
2. **Redis ZSet 缓存（`sp:home:post:list`）**：
   - **Member**：`postId`
   - **Score**：发帖时间的毫秒时间戳（`toEpochMilli()`）
   - **检索方式**：`ZREVRANGEBYSCORE` 按分数从大到小（最新在前）快速检索。

### 4.2 容器与服务启动自愈（DataInitializer）
项目启动时，`DataInitializer` 会自动执行自检与预热：
1. 检查 `home_post` 表是否为空，若为空自动从 `post` 表同步全部有效帖子；
2. 检查 Redis `sp:home:post:list` 是否为空，若未预热则批量将最新帖子推入 ZSet 并设置 7 天 TTL；
3. 检查 Elasticsearch `post` 索引，若缺失自动创建 Mapping 并同步全量帖子文本。

---

## 5. 游标分页（Scroll Pagination）设计与实现

为了满足移动端与 Web 端的**无限滚动（Infinite Scroll）**体验，并杜绝传统 `OFFSET-LIMIT` 分页在新增帖子时出现的“数据重复”与“漏看”问题，系统实现了基于时间戳游标的高性能分页模型。

### 5.1 数据传输对象 `ScrollResult<T>`

```java
public class ScrollResult<T> {
    private List<T> list;      // 当前页数据列表
    private Long minTime;      // 本次返回数据的最小时间戳（作为下一次请求的 lastId 游标）
    private Integer offset;    // 偏移量（用于跳过时间戳相同的重复元素）
}
```

### 5.2 游标计算逻辑

```java
// Redis ZSet 逆序范围查询
Set<ZSetOperations.TypedTuple<Object>> typedTuples = redisTemplate.opsForZSet()
        .reverseRangeByScoreWithScores(homePostKey, 0, lastId, offset, pageSize);

// 计算下一页的 minTime 与 offset
int newOffset = 1;
double lastScore = scores.get(0);
for (int i = 1; i < scores.size(); i++) {
    if (lastScore == scores.get(i)) {
        newOffset++; // 遇到相同时间戳，偏移量递增
    } else {
        newOffset = 1; // 遇到新时间戳，重置偏移量为 1
    }
    lastScore = scores.get(i);
}

ScrollResult<PostVO> result = new ScrollResult<>();
result.setList(postVOS);
result.setMinTime(scores.get(scores.size() - 1).longValue());
result.setOffset(newOffset);
```

- **初次请求**：前端传入 `lastId = 当前时间戳毫秒值`，`offset = 0`；
- **后续请求**：前端将上一页返回的 `minTime` 传入 `lastId`，将返回的 `offset` 传入 `offset` 参数；
- **精准去重**：即使多篇帖子具有完全相同的发帖时间戳毫秒值，通过 `offset` 计数器也能保证分页完全平滑且不漏不错。

---

## 6. 缓存与性能优化策略总结

1. **读写分离与缓存直读**：
   - 首页流列表仅拉取 `postId` 序列，具体的点赞数（`sp:like:count:{id}`）与是否点赞（`sp:like:{id}`）通过 `DataCacheUtil` 从 Redis 高速聚合，无连表查询开销。
2. **防雪崩与主动续期**：
   - 首页 ZSet 与用户兴趣画像均设置 7 天滑动过期时间，有读写请求时自动执行 `EXPIRE` 续期。
3. **文本检索与分词**：
   - 帖子内容入库时使用 `HtmlUtils.htmlToPlainText` 提取纯文本存入 Elasticsearch，并配置 `ik_smart` 分词器，保证索引紧凑与检索高效。
