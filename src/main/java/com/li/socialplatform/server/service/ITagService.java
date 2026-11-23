package com.li.socialplatform.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.li.socialplatform.pojo.entity.Tag;
import com.li.socialplatform.pojo.vo.TagVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 帖子标签服务
 */
public interface ITagService extends IService<Tag> {

    int MAX_TAGS_PER_POST = 5;
    int MAX_TAG_LENGTH = 20;

    /**
     * 规范化并校验原始标签列表（去 # 号、去空白、大小写不敏感去重、长度与数量限制）
     */
    List<String> normalizeTagNames(List<String> rawNames);

    /**
     * 为帖子解析并绑定标签，返回绑定后的标签视图列表
     */
    List<TagVO> bindPostTags(Long postId, Integer categoryId, List<String> rawNames, boolean isPrivate);

    /**
     * 帖子私密状态变更时同步标签公开使用次数
     */
    void onPostPrivacyChanged(Long postId, boolean wasPrivate, boolean nowPrivate);

    /**
     * 帖子删除时清理关联并回退公开使用次数
     */
    void onPostDeleted(Long postId, boolean wasPrivate);

    /**
     * 批量查询帖子对应的标签列表（无 N+1 查询）
     */
    Map<Long, List<TagVO>> getTagsByPostIds(Collection<Long> postIds);

    /**
     * 批量查询帖子对应的标签 ID 列表（用于推荐特征提取）
     */
    Map<Long, List<Long>> getTagIdsByPostIds(Collection<Long> postIds);

    /**
     * 获取热门/推荐标签（可选按分类过滤）
     */
    List<TagVO> getHotTags(Integer categoryId, Integer limit);

    /**
     * 按关键词模糊检索标签（用于发帖标签输入联想）
     */
    List<TagVO> searchTags(String keyword, Integer categoryId, Integer limit);

    /**
     * 按 ID 或名称解析启用的标签实体
     */
    Tag resolveTag(Long tagId, String tagName);

    /**
     * 清理热门标签缓存
     */
    void evictHotTagCache();

    /**
     * 获取标签详情（标签信息 + 帖子数 + 最新帖子）
     */
    Map<String, Object> getTagDetail(Long tagId);
}
