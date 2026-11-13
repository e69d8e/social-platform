package com.li.socialplatform.pojo.entity;

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
 * 用户标签兴趣评分实体
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("user_tag_interest")
public class UserTagInterest implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("tag_id")
    private Long tagId;

    @TableField("score")
    private Integer score;

    @TableField("update_time")
    private LocalDateTime updateTime;

    public UserTagInterest(Long userId, Long tagId, Integer score) {
        this.userId = userId;
        this.tagId = tagId;
        this.score = score;
        this.updateTime = LocalDateTime.now();
    }
}
