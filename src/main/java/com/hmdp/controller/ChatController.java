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
        String userMessage = request.get("message");
        String reply = chatService.chat(userMessage);
        Map<String, String> response = new HashMap<>();
        response.put("reply", reply);
        return response;
    }
}
