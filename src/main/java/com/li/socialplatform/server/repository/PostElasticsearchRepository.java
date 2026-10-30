package com.li.socialplatform.server.repository;

import com.li.socialplatform.pojo.entity.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.CountQuery;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface PostElasticsearchRepository extends ElasticsearchRepository<Post, Long> {

    /**
     * 未封禁且非私密的帖子。历史 ES 文档可能缺省 isPrivate 字段：
     * must_not term 对缺字段文档天然放行，不会误杀存量数据。
     */
    @Query("""
            {"bool": {"filter": [{"term": {"enabled": true}}], "must_not": [{"term": {"isPrivate": true}}]}}
            """)
    List<Post> findByEnabledTrue(Pageable pageable);

    // count 必须用 @CountQuery：@Query + 标量返回不会被执行为 count 查询，
    // Spring Data 会把命中的 Post 文档强转成 long 抛出 ClassCastException
    @CountQuery("""
            {"bool": {"filter": [{"term": {"enabled": true}}], "must_not": [{"term": {"isPrivate": true}}]}}
            """)
    long countByEnabledTrue();

    @Query("""
            {"bool": {"filter": [{"term": {"enabled": true}}, {"term": {"categoryId": "?0"}}], "must_not": [{"term": {"isPrivate": true}}]}}
            """)
    List<Post> findByCategoryIdAndEnabledTrue(Integer categoryId, Pageable pageable);

    @CountQuery("""
            {"bool": {"filter": [{"term": {"enabled": true}}, {"term": {"categoryId": "?0"}}], "must_not": [{"term": {"isPrivate": true}}]}}
            """)
    long countByCategoryIdAndEnabledTrue(Integer categoryId);

    /**
     * 关键词搜索（标题或内容），只返回未封禁且非私密的帖子。
     * 用 @Query 而非方法名推导：Or 与 And 混用时 Spring Data 的拆分语义易出错。
     * 调用方（UserServiceImpl.listPost）的 count 用同条件的 NativeQuery，两边需保持一致。
     */
    @Query("""
            {"bool": {"should": [
                {"match": {"title": "?0"}},
                {"match": {"content": "?0"}}
            ], "minimum_should_match": 1, "filter": [{"term": {"enabled": true}}], "must_not": [{"term": {"isPrivate": true}}]}}
            """)
    List<Post> searchByKeywordAndEnabled(String keyword, Pageable pageable);
}
