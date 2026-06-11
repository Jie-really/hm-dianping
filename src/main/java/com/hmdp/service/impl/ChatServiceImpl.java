package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.VoucherMapper;
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
    @Autowired
    private VoucherMapper voucherMapper;

    private static final String CHAT_HISTORY_KEY = "chat:history:";
    private static final int MAX_HISTORY_SIZE = 20;


    // 创建AI客户端
    @PostConstruct
    public void init() {
        this.client = new ClientV4.Builder(apiKey)
                .networkConfig(60, 60, 60, 60, TimeUnit.SECONDS)
                .enableTokenCache()
                .build();
        log.info("智谱AI客户端初始化成功");
    }

    @Override
    public String chat(String sessionId, String userMessage) {
        String historyKey = CHAT_HISTORY_KEY + sessionId;

        // 1. 获取历史消息（原有逻辑）
        List<String> historyJson = stringRedisTemplate.opsForList().range(historyKey, 0, -1);
        if (historyJson == null) historyJson = new ArrayList<>();

        // 2. 准备系统提示
        String systemPrompt = "你是黑马点评的智能客服助手，回答要简洁、准确、礼貌。";

        // 3. 判断是否与优惠券相关（简单关键词匹配，可后续升级）
        if (userMessage.contains("优惠券") || userMessage.contains("券") || userMessage.contains("折扣") || userMessage.contains("满减")) {
            String voucherData = buildVoucherInfo();
            systemPrompt += "\n\n【真实优惠券数据】\n" + voucherData;
            systemPrompt += "\n请基于以上真实数据回答用户问题。如果用户问的优惠券不存在，请明确告知。禁止编造数据。";
        }

        // 4. 构建消息列表
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), systemPrompt));

        // 添加历史消息（原有逻辑，注意跳过历史中的system消息）
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

        // 5. 调用智谱 API（原有代码）
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("glm-4-flash")
                .stream(Boolean.FALSE)
                .invokeMethod(Constants.invokeMethod)
                .messages(messages)
                .build();
        ModelApiResponse response = client.invokeModelApi(request);
        String reply = response.getData().getChoices().get(0).getMessage().getContent().toString();
        log.info("AI回复: {}", reply);

        // 6. 存储本次对话到 Redis（原有代码）
        stringRedisTemplate.opsForList().rightPush(historyKey, "user:" + userMessage);
        stringRedisTemplate.opsForList().rightPush(historyKey, "assistant:" + reply);
        stringRedisTemplate.opsForList().trim(historyKey, -MAX_HISTORY_SIZE, -1);
        stringRedisTemplate.expire(historyKey, 30, TimeUnit.MINUTES);

        return reply;
    }

    private String buildVoucherInfo() {
        String cacheKey = "voucher:list:available";
        // 1. 尝试从Redis获取缓存
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StrUtil.isNotBlank(cached)) {
            log.info("命中优惠券缓存");
            return cached;
        }
        // 2. 缓存未命中，查询数据库
        log.info("缓存未命中，查询数据库");
        QueryWrapper<Voucher> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1)
                .orderByDesc("create_time")
                .last("limit 10");
        List<Voucher> vouchers = voucherMapper.selectList(wrapper);
        String voucherInfo;
        if (vouchers == null || vouchers.isEmpty()) {
            voucherInfo = "当前暂无有效优惠券。";
        } else {
            StringBuilder sb = new StringBuilder();
            for (Voucher v : vouchers) {
                sb.append(String.format(
                        "- 优惠券ID:%d, 名称:%s, 使用规则:%s, 库存:%d, 有效期:%s~%s\n",
                        v.getId(), v.getTitle(), v.getRules(),v.getStock(),
                        v.getBeginTime() == null ? "无限制" : v.getBeginTime(),
                        v.getEndTime() == null ? "无限制" : v.getEndTime()
                ));
            }
            voucherInfo = sb.toString();
        }
        // 3. 写入缓存，过期时间5分钟
        stringRedisTemplate.opsForValue().set(cacheKey, voucherInfo, 5, TimeUnit.MINUTES);
        return voucherInfo;
    }
}
