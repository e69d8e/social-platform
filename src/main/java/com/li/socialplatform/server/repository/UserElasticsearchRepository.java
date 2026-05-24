package com.li.socialplatform.server.repository;

import com.li.socialplatform.pojo.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

/**
 * @author e69d8e
 * @since 2026/05/06 12:49
 */
public interface UserElasticsearchRepository extends ElasticsearchRepository<User, Long> {
    List<User> findByUsernameOrNickname(String username, String nickname, Pageable pageable);
    long countByUsernameOrNickname(String username, String nickname);
}
