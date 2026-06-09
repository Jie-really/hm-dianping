package com.hmdp.service.impl;

import com.hmdp.service.IChatService;
import com.zhipu.oapi.ClientV4;
import com.zhipu.oapi.Constants;
import com.zhipu.oapi.service.v4.model.ChatCompletionRequest;
import com.zhipu.oapi.service.v4.model.ChatMessage;
import com.zhipu.oapi.service.v4.model.ChatMessageRole;
import com.zhipu.oapi.service.v4.model.ModelApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ChatServiceImpl implements IChatService{

    //读取api
    @Value("${zhipu.api-key}")
    private String apiKey;

    private ClientV4 client;

    // 创建AI客户端
    @PostConstruct
    public void init() {
        this.client = new ClientV4.Builder(apiKey).build();
        log.info("智谱AI客户端初始化成功！");
    }

    /**
     * @param userMessage 用户发来的消息
     * @return AI返回的回复
     */
    public String chat(String userMessage) {
        // 1. 构造消息列表，角色是"user"，内容就是用户的消息
        List<ChatMessage> messages = new ArrayList<>();
        ChatMessage chatMessage = new ChatMessage(ChatMessageRole.USER.value(), userMessage);
        messages.add(chatMessage);
        // 2. 构建一个请求对象，告诉AI我们的意图
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("glm-4-flash")  // 使用完全免费的模型
                .stream(Boolean.FALSE) // 非流式，一次性返回所有结果
                .invokeMethod(Constants.invokeMethod) // 设置为通用调用方式
                .messages(messages)    // 放入我们的消息
                .build();

        // 3. 调用客户端发送请求，并获取响应
        ModelApiResponse response = client.invokeModelApi(request);
        // 4. 从响应中提取AI的回复文本
        String reply = response.getData().getChoices().get(0).getMessage().getContent().toString();
        log.info("AI回复: {}", reply);
        return reply;
    }
}
