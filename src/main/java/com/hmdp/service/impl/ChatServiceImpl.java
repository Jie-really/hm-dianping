package com.hmdp.service.impl;

import com.hmdp.service.IChatService;
import com.zhipu.oapi.ClientV4;
import com.zhipu.oapi.Constants;
import com.zhipu.oapi.service.v4.model.ChatCompletionRequest;
import com.zhipu.oapi.service.v4.model.ChatMessage;
import com.zhipu.oapi.service.v4.model.ChatMessageRole;
import com.zhipu.oapi.service.v4.model.ModelApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ChatServiceImpl implements IChatService{

    //读取api
    @Value("${zhipu.api-key}")
    private String apiKey;

    private ClientV4 client;

    @Autowired
    private StringRedisTemplate  stringRedisTemplate;

    private static final String CHAT_HISTORY_KEY = "chat:history:";
    private static final int MAX_HISTORY_SIZE = 20;


    // 创建AI客户端
    @PostConstruct
    public void init() {
        this.client = new ClientV4.Builder(apiKey)
                .networkConfig(60, 60, 60, 60, TimeUnit.SECONDS)
                .enableTokenCache()
                .build();
        log.info("智谱AI客户端初始化成功（超时时间已调整）");
    }

    public String chat(String sessionId, String userMessage) {
        String historyKey = CHAT_HISTORY_KEY + sessionId;
        // 1. 从 Redis 获取历史消息
        List<String> historyJson = stringRedisTemplate.opsForList()
                .range(historyKey, 0, -1);
        if (historyJson == null) {
            historyJson = new ArrayList<>();
        }
        // 2. 构建消息列表
        List<ChatMessage> messages = new ArrayList<>();
        // 添加系统提示
        messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(),
                "你是黑马点评的智能客服助手，回答要简洁、准确、礼貌。"));
        // 添加历史消息
        int start = Math.max(0, historyJson.size() - MAX_HISTORY_SIZE);
        for (int i = start; i < historyJson.size(); i++) {
            String line = historyJson.get(i);
            if (line.startsWith("user:")) {
                messages.add(new ChatMessage(ChatMessageRole.USER.value(), line.substring(5)));
            } else if (line.startsWith("assistant:")) {
                messages.add(new ChatMessage(ChatMessageRole.ASSISTANT.value(), line.substring(10)));
            }
        }
        // 添加当前用户消息
        messages.add(new ChatMessage(ChatMessageRole.USER.value(), userMessage));
        // 3. 调用智谱 API
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("glm-4-flash")
                .stream(Boolean.FALSE)
                .invokeMethod(Constants.invokeMethod)
                .messages(messages)
                .build();
        ModelApiResponse response = client.invokeModelApi(request);
        String reply = response.getData().getChoices().get(0).getMessage().getContent().toString();
        log.info("AI回复: {}", reply);
        // 4. 存储本次对话到 Redis（右推，保持顺序）
        stringRedisTemplate.opsForList().rightPush(historyKey, "user:" + userMessage);
        stringRedisTemplate.opsForList().rightPush(historyKey, "assistant:" + reply);
        // 保留最近 MAX_HISTORY_SIZE 条，超出则裁剪
        stringRedisTemplate.opsForList().trim(historyKey, -MAX_HISTORY_SIZE, -1);
        // 设置过期时间（30分钟无活动则清空）
        stringRedisTemplate.expire(historyKey, 30, TimeUnit.MINUTES);
        return reply;
    }
}
