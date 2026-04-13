package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.View;

import javax.servlet.http.HttpSession;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    private final View error;

    public UserServiceImpl(View error) {
        this.error = error;
    }

    /**
     * 发送手机验证码
     *
     * @param phone 登录参数，包含手机号、验证码；或者手机号、密码
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.检验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            //2.如果不符合，返回错误信息
            return Result.error("手机号码格式错误");
        }

        //3.如果符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        //4.保存验证码到session
        session.setAttribute("code",code);

        //5.发送验证码，做个样子，公司会有专门的程序实现这块
        log.info("发送验证码成功");
        //6.返回
        return Result.success();
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            //2.如果不符合，返回错误信息
            return Result.error("手机号码格式错误");
        }
        //2.校验验证码
        String code = loginForm.getCode();
        Object cacheCode = session.getAttribute("code");
        if(code==null||!code.equals(cacheCode)){
            //3.不符合，返回错误信息
            return Result.error("验证码错误");
        }
        //4.符合，查询用户
        User user = query().eq("phone",phone).eq("code",code).one();
        //5.判断用户是否存在
        if(user==null){
            // 6.不存在，创建用户并保存
            user=createUserWithhPhone(phone);
        }

        //7.将用户保存到session
        session.setAttribute("user",user);
        return Result.success();

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
