package com.li.socialplatform.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author e69d8e
 * @since 2025/12/8 22:10
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserVO implements Serializable {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;
    private String username;
    private String avatar;
    private String bio;
    private Integer gender;
    private Integer authorityId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    private String nickname;
    private Boolean enabled;

    private Boolean fansPrivate;
    private Boolean followPrivate;

    private Boolean followed;
    private Integer count;
}
