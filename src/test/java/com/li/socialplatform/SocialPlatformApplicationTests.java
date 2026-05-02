package com.li.socialplatform;

import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.mapper.PostMapper;
import com.li.socialplatform.mapper.UserMapper;
import com.li.socialplatform.pojo.entity.Post;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Date;
import java.util.List;

@SpringBootTest
class SocialPlatformApplicationTests {

    @Test
    void contextLoads() {
        System.out.println(new Date().getTime());
    }

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;
    @Autowired
    private PostMapper postMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;
    @Test
    void testAddPostElasticSearch() {
        try {
            // 将数据库中的数据导入ElasticSearch
            postMapper.selectList(null).forEach(post -> {
                Integer count = (Integer) redisTemplate.opsForValue().get(KeyConstant.LIKE_COUNT + post.getId());
                post.setCount(count == null ? 0 : count);
                elasticsearchOperations.save(post);
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    void testAddUserElasticSearch() {
        try {
            // 将数据库中的数据导入ElasticSearch
            userMapper.selectList(null).forEach(user -> {
                Integer count = (Integer) redisTemplate.opsForValue().get(KeyConstant.FOLLOW_COUNT_KEY + user.getId());
                user.setCount(count == null ? 0 : count);
                elasticsearchOperations.save(user);
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 查询
    @Test
    void testQuery() {
        // 创建查询对象
        Criteria criteria = Criteria.where("title").contains("11");
        Query query = new CriteriaQuery(criteria);
        SearchHits<Post> hits = elasticsearchOperations.search(query, Post.class);
        List<Post> posts = hits.stream().map(SearchHit::getContent).toList();
        System.out.println(posts);
        // 获取查询结果
//        hits.getSearchHits().forEach(hit -> {
//            System.out.println(hit.getContent());
//        });
    }

    @Test
    void notifyMessage () {
        simpMessagingTemplate.convertAndSendToUser("li", "/queue/msg", "点赞了你的帖子");
    }

    // 清空Elasticsearch中的数据
    @Test
    void clearElasticsearchData() {
        elasticsearchOperations.indexOps(Post.class).delete();
        elasticsearchOperations.indexOps(Post.class).create();
        elasticsearchOperations.indexOps(Post.class).putMapping();
    }
}
