package com.li.socialplatform.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 图表数据项，用于时间序列曲线展示
 *
 * @author e69d8e
 * @since 2026/06/02
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChartItemVO implements Serializable {
    /**
     * 日期标签，如 "2025-12-01" 或 "2025-W01"
     */
    private String date;

    /**
     * 数值
     */
    private Long count;
}
