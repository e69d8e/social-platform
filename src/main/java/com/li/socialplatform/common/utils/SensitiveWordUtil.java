package com.li.socialplatform.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 敏感词过滤工具（基于 DFA 算法）
 * 词库文件: classpath:sensitive-words.txt，每行一个词，# 开头为注释
 * 风险等级格式: 词汇|等级 (0=安全 1=低 2=中 3=高)，默认 1
 *
 * @author e69d8e
 */
@Slf4j
@Component
public class SensitiveWordUtil {

    /** DFA 字典树 */
    private final Map<Character, Object> trieRoot = new HashMap<>();
    /** 词 -> 风险等级 */
    private final Map<String, Integer> wordLevelMap = new HashMap<>();
    private static final String END_FLAG = "isEnd";
    private static final String LEVEL_KEY = "level";

    @PostConstruct
    public void init() {
        try {
            var resource = new ClassPathResource("sensitive-words.txt");
            if (!resource.exists()) {
                log.info("sensitive-words.txt 不存在，跳过敏感词初始化");
                return;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int level = 1;
                    if (line.contains("|")) {
                        String[] parts = line.split("\\|");
                        line = parts[0].trim();
                        try { level = Integer.parseInt(parts[1].trim()); } catch (NumberFormatException ignored) {}
                    }
                    addWord(line, level);
                    count++;
                }
                log.info("敏感词库加载完成，共 {} 个词", count);
            }
        } catch (Exception e) {
            log.error("加载敏感词库失败: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void addWord(String word, int level) {
        wordLevelMap.put(word, level);
        Map<Character, Object> current = trieRoot;
        for (char c : word.toCharArray()) {
            current = (Map<Character, Object>) current.computeIfAbsent(c, k -> new HashMap<>());
        }
        current.put((Character) END_FLAG.charAt(0), END_FLAG);
        // 存储等级到 Map 中
        Map<Character, Object> endNode = current;
        endNode.put(LEVEL_KEY.charAt(0), level);
    }

    /**
     * 检测文本中的敏感词，返回最高风险等级（0=安全）
     */
    public int checkRiskLevel(String text) {
        if (text == null || text.isEmpty()) return 0;
        int maxLevel = 0;
        Set<String> found = findAll(text);
        for (String word : found) {
            int level = wordLevelMap.getOrDefault(word, 1);
            if (level > maxLevel) maxLevel = level;
        }
        return maxLevel;
    }

    /**
     * 找出文本中所有敏感词
     */
    @SuppressWarnings("unchecked")
    public Set<String> findAll(String text) {
        Set<String> result = new HashSet<>();
        if (text == null) return result;
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            Map<Character, Object> current = trieRoot;
            StringBuilder sb = new StringBuilder();
            for (int j = i; j < chars.length; j++) {
                Object node = current.get(chars[j]);
                if (node == null) break;
                current = (Map<Character, Object>) node;
                sb.append(chars[j]);
                if (current.containsKey((Character) END_FLAG.charAt(0))) {
                    result.add(sb.toString());
                }
            }
        }
        return result;
    }

    /**
     * 替换敏感词为 ***
     */
    public String filter(String text) {
        if (text == null || text.isEmpty()) return text;
        Set<String> words = findAll(text);
        for (String word : words) {
            text = text.replace(word, "*".repeat(word.length()));
        }
        return text;
    }
}
