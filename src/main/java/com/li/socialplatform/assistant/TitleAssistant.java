package com.li.socialplatform.assistant;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "openAiChatModel"
)
public interface TitleAssistant {

    @SystemMessage("你是一个会话标题生成器。根据用户的第一条消息，生成一个简短精炼的会话标题。标题不超过20个字符。只返回标题文本，不要有任何其他内容、标点或引号。")
    String generateTitle(@UserMessage String message);
}
