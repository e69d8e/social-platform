package com.li.socialplatform.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 帖子-标签关联实体
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("post_tag")
public class PostTag implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("post_id")
    private Long postId;

    @TableField("tag_id")
    private Long tagId;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public PostTag(Long postId, Long tagId) {
        this.postId = postId;
        this.tagId = tagId;
    }
}
