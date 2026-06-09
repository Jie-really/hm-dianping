package com.hmdp.service;

public interface IChatService {
    /**
     * 与AI客服对话
     * @param userMessage 用户输入的消息
     * @return AI返回的回复
     */
    String chat(String sessionId, String userMessage);
}
