package com.li.socialplatform.recommend;

import com.li.socialplatform.common.utils.UserIntersetScoreUtil;
import com.li.socialplatform.server.service.impl.TagServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InterestAndTagLogicTest {

    @Test
    void exponentialTimeDecayFollowsHalfLife() {
        long now = 1_790_000_000L;
        long fourteenDaysAgo = now - 14L * 86400L;
        long twentyEightDaysAgo = now - 28L * 86400L;

        assertEquals(100.0, UserIntersetScoreUtil.applyTimeDecay(100, now, now, 14.0), 1e-6);
        assertEquals(50.0, UserIntersetScoreUtil.applyTimeDecay(100, fourteenDaysAgo, now, 14.0), 1e-6);
        assertEquals(25.0, UserIntersetScoreUtil.applyTimeDecay(100, twentyEightDaysAgo, now, 14.0), 1e-6);
        assertEquals(0.0, UserIntersetScoreUtil.applyTimeDecay(-5, now, now, 14.0), 1e-6);
    }

    @Test
    void tagNormalizationCleansDeduplicatesAndValidatesLimits() {
        TagServiceImpl service = new TagServiceImpl(null, null, null);
        List<String> normalized = service.normalizeTagNames(Arrays.asList(
                " #Spring  Boot ",
                "spring boot",
                "#Vue3",
                "   ",
                null,
                "Redis"
        ));
        assertEquals(List.of("Spring Boot", "Vue3", "Redis"), normalized);

        assertThrows(IllegalArgumentException.class,
                () -> service.normalizeTagNames(List.of("1", "2", "3", "4", "5", "6")));

        assertThrows(IllegalArgumentException.class,
                () -> service.normalizeTagNames(List.of("这是一个超过二十个字符的超级长标签名称测试")));
    }
}
