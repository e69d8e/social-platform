package com.li.socialplatform.assistant;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * AI 内容创作助手：生成标题、润色内容、总结评论
 */
@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "openAiChatModel"
)
public interface ContentAssistant {

    @SystemMessage("你是一个社区帖子标题生成器。根据用户提供的帖子内容，生成3个吸引人的标题选项。每个标题不超过30字，风格各异（一个正式、一个轻松、一个悬念）。用换行分隔，不要编号。")
    String generateTitles(@UserMessage String content);

    @SystemMessage("你是一个社区内容润色助手。请润色用户提供的帖子内容，使其更流畅、有吸引力，但保持原意不变。不要添加任何解释，只返回润色后的内容。")
    String polishContent(@UserMessage String content);

    @SystemMessage("你是一个评论总结助手。请总结以下帖子评论区的讨论要点，提取主要观点和争议点，用2-3句话概括。只返回总结文本。")
    String summarizeComments(@UserMessage String comments);
}
