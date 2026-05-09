package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

//全局Id生成器
@Component
public class RedisIdWorker {

    private static final long BIG_TIMESTAMP=1640995200;
    private static final int COUNT_BITS=32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public Long nextId(String prefix) {
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowsecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowsecond - BIG_TIMESTAMP;
        //2.生成序列号
        //2.1获取当前日期，精确代天（序列号只有32bit,防止超范围，一天一结，并且也可以根据key看当天订单量）
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //2.2自增长
        long count = stringRedisTemplate.opsForValue().increment("icr:" + prefix + ":" + date);//后续要运算，不用包装类
        //3.拼接并返回
        return timestamp<<COUNT_BITS | count;
    }

    public static void   main(String[] args){
        LocalDateTime time = LocalDateTime.of(2022, 1, 1, 0, 0, 0);
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println(second);
    }
}
