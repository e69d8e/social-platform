package com.li.socialplatform.server.repository;

import com.li.socialplatform.pojo.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

/**
 * @author e69d8e
 * @since 2026/05/06 12:49
 */
public interface UserElasticsearchRepository extends ElasticsearchRepository<User, Long> {

    @Query("""
            {"bool": {"must": [{"multi_match": {"query": "?0", "fields": ["username.edge", "nickname.edge"], "type": "best_fields"}}], "filter": [{"term": {"enabled": true}}]}}
            """)
    List<User> findByUsernameOrNickname(String username, String nickname, Pageable pageable);
}
