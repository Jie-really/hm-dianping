package com.hmdp.controller;

import com.hmdp.service.IChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private IChatService chatService;

    @PostMapping("/ask")
    public Map<String, String> ask(@RequestBody Map<String, String> request) {
        String sessionId = request.get("sessionId");
        String userMessage = request.get("message");
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = "defauld";
        }
        String reply = chatService.chat(sessionId,userMessage);
        Map<String, String> response = new HashMap<>();
        response.put("reply", reply);
        return response;
    }
}
