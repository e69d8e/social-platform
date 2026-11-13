package com.li.socialplatform.common.utils;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;

import java.util.Collection;
import java.util.List;

/**
 * Elasticsearch 查询构造小工具（避免同一种过滤条件在多个 service 里各写一遍）
 *
 * @author e69d8e
 */
public final class EsQueryUtil {

    private EsQueryUtil() {
    }

    /**
     * 构造「字段值不在给定 ID 集合内」的排除条件，配合 {@code bool.mustNot(...)} 使用。
     *
     * <p>注意：ES 里被映射为 {@code Keyword} 的 Long 字段（如 {@code Post.userId}、{@code User.id}）
     * 实际存的是字符串，因此值必须转成 String 再传，否则永远匹配不上。</p>
     *
     * @param field 字段名（keyword 类型）
     * @param ids   需要排除的 ID 集合，为空时返回 null（调用方跳过即可）
     */
    public static Query excludeKeywordField(String field, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        List<FieldValue> values = ids.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .map(id -> FieldValue.of(String.valueOf(id)))
                .toList();
        if (values.isEmpty()) {
            return null;
        }
        return Query.of(q -> q.terms(t -> t.field(field).terms(tv -> tv.value(values))));
    }
}
