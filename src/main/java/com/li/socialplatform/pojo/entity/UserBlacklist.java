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
 * 用户黑名单（用户级拉黑）。
 *
 * <p>与 {@link BanRecord} 的区别：BanRecord 是管理员/审核员的封禁，属于平台治理；
 * 这里是普通用户之间的拉黑，效果是双向互不可见 + 互不可私信。</p>
 *
 * @author e69d8e
 */
@TableName("user_blacklist")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserBlacklist implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 拉黑发起者 */
    @TableField("user_id")
    private Long userId;

    /** 被拉黑用户 */
    @TableField("blocked_user_id")
    private Long blockedUserId;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
