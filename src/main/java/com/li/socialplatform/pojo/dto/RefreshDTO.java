package com.li.socialplatform.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author e69d8e
 * @since 2026/05/10 15:00
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RefreshDTO {
    @NotBlank(message = "refreshToken不能为空")
    private String refreshToken;
}
