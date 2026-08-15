package com.li.socialplatform.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import reactor.core.publisher.Flux;

@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT, // 指定如何自动装配AI服务所需的依赖组件 EXPLICIT模式：显式装配模式，需要明确指定每个依赖组件的Bean名称 与之相对的是IMPLICIT隐式模式，会自动查找匹配的Bean
        chatMemoryProvider = "chatMemoryProvider", // 提供聊天记忆管理功能，用于保存和恢复对话上下文
        streamingChatModel = "openAiStreamingChatModel" // 指定使用的流式聊天大语言模型
)
public interface Assistant {
    @SystemMessage(fromResource = "system-prompt.txt") // 系统提示词
    Flux<String> chat(@MemoryId String memoryId, @UserMessage String message);
}
