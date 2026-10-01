package com.li.socialplatform.server.repository;

import com.li.socialplatform.pojo.entity.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface PostElasticsearchRepository extends ElasticsearchRepository<Post, Long> {

    List<Post> findByEnabledTrue(Pageable pageable);

    long countByEnabledTrue();

    List<Post> findByCategoryIdAndEnabledTrue(Integer categoryId, Pageable pageable);

    long countByCategoryIdAndEnabledTrue(Integer categoryId);

    /**
     * 关键词搜索（标题或内容），只返回未封禁（enabled=true）的帖子。
     * 用 @Query 而非方法名推导：Or 与 And 混用时 Spring Data 的拆分语义易出错。
     * 调用方（UserServiceImpl.listPost）的 count 用同条件的 NativeQuery，两边需保持一致。
     */
    @Query("""
            {"bool": {"should": [
                {"match": {"title": "?0"}},
                {"match": {"content": "?0"}}
            ], "minimum_should_match": 1, "filter": [{"term": {"enabled": true}}]}}
            """)
    List<Post> searchByKeywordAndEnabled(String keyword, Pageable pageable);
}
