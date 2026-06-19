package com.li.socialplatform.pojo.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author e69d8e
 * @since 2025/12/8 17:01
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO implements Serializable {
    private String username;
    private String password;

    @Size(max = 16, message = "昵称长度不能超过16")
    private String nickname;
    private String oldAvatar;
    private String avatar;
    @Size(max = 2000, message = "简介长度不能超过2000")
    private String bio;
    private Integer gender;
    // 仅管理员
    private String authority;
    private Boolean enabled;

    private Boolean fansPrivate;
    private Boolean followPrivate;
}
