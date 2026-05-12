package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.View;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.检验手机号
        log.info("开始检验手机号：{}",phone);
        if(RegexUtils.isPhoneInvalid(phone)){
            //2.如果不符合，返回错误信息
            return Result.error("手机号码格式错误");
        }
        log.info("手机号检验成功！");

        //3.如果符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        //4.保存验证码到Redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //5.发送验证码，做个样子，公司会有专门的程序实现这块
        log.debug("发送验证码成功,验证码：{}",code);
        //6.返回
        return Result.success();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        //1.校验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            //2.如果不符合，返回错误信息
            return Result.error("手机号码格式错误");
        }
        //3.从Redis中取得验证码并校验
        String code = loginForm.getCode();
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        if(code==null||!code.equals(cacheCode)){
            //不符合，返回错误信息
            return Result.error("验证码错误");
        }

        //3.符合，查询用户
        User user = query().eq("phone",phone).one();

        //4.判断用户是否存在
        if(user==null){
            // 5.不存在，创建用户并保存
            user = createUserWithhPhone(phone);
        }

        //6.将用户保存到redis
        //6.1随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString().replaceAll("-","");
        //6.2将User对象转为Hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user,UserDTO.class);
        Map<String, String> userMap = new HashMap<>();
        userMap.put("id", String.valueOf(userDTO.getId()));        // Long 转 String
        userMap.put("nickName", userDTO.getNickName() != null ? userDTO.getNickName() : "");
        userMap.put("icon", userDTO.getIcon() != null ? userDTO.getIcon() : "");
        //6.3存储
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,userMap);
        //6.4设置有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL, TimeUnit.MINUTES);
        //7.返回token
        return Result.success(token);
    }

    private User createUserWithhPhone(String phone) {
        //1.创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomNumbers(6));
        //2.保存用户
        save(user);
        return user;
    }
}
