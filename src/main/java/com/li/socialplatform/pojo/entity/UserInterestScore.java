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
 * @author e69d8e
 * @since 2026/05/23
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_interest_score")
public class UserInterestScore implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("category_id")
    private Integer categoryId;

    @TableField("score")
    private Integer score;

    @TableField("update_time")
    private LocalDateTime updateTime;

    public UserInterestScore(Long userId, Integer categoryId, Integer score) {
        this.userId = userId;
        this.categoryId = categoryId;
        this.score = score;
        this.updateTime = LocalDateTime.now();
    }
}
