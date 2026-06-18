package com.li.socialplatform.common.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author e69d8e
 * @since 2025/12/8 17:31
 */
@Data
@Component
@ConfigurationProperties(prefix = "social-platform.system-constants")
public class SystemConstants {
    public String userNicknamePrefix;
    public String imageUploadDir;
    public String baseUrl;
    public String defaultPageSize;
    public String maxPageSize;
    public String defaultAvatar;
    public String titleMaxLength;
    public String contentMaxLength;
    public String nicknameMaxLength;
    public String bioMaxLength;
}
