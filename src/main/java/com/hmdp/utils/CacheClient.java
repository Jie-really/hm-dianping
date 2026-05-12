package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value,Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value,Long time, TimeUnit unit) {
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    //缓存穿透
    public <R,ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback,Long time, TimeUnit unit){
        String key=keyPrefix + id;
        //在Redis里查询
        log.info("开始在Redis中查询：{}",id);
        String json = stringRedisTemplate.opsForValue().get(key);
        //存在，返回
        if(StrUtil.isNotBlank(json)){
            return JSONUtil.toBean(json,type);
        }
        //判断是否为空值
        if(json!=null){
            return null;
        }
        //不存在，在sql里查询
        R r = dbFallback.apply(id);
        //不存在，返回错误信息,为防止缓存穿透，将信息保存在Redis
        if(r==null){
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //存在，将信息保存到Redis里
        this.set(key, r,time,unit);
        //返回
        return r;
    }

    private static final ExecutorService CACHE_REBUID_EXECUTOR = Executors.newFixedThreadPool(10);//线程池

    //缓存击穿（逻辑过期）
    public <R,ID> R queryWithLogicalExpire(
            String keyPrefix,ID id,Class<R> type, Function<ID,R> dbFallback,Long time, TimeUnit unit){
        String key=keyPrefix + id;
        //1.在Redis里查询
        log.info("开始在Redis中查询：{}",id);
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.不存在，返回
        if(StrUtil.isBlank(json)){
            return null;
        }
        //3.存在，反序列化JSON
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //4.判断是否过期
        if(expireTime != null && expireTime.isAfter(LocalDateTime.now())){
            //5.未过期，直接返回店铺信息
            return r;
        }
        //6.过期，缓存重建
        //6.1获取互斥锁
        String lockKey=LOCK_SHOP_KEY + id;
        boolean lock = tryLock(lockKey);
        //6.2判断是否取锁成功
        if(lock){
            //6.3.成功，开启独立线程，缓存重建
            CACHE_REBUID_EXECUTOR.submit(()->{
                //查数据库
                R r1 = dbFallback.apply(id);
                //写入Redis
                this.setWithLogicalExpire(key, r1,time,unit);
            });
        }
        //6.4.返回过期的商铺信息
        return r;
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }

    private void  unLock(String key){
        stringRedisTemplate.delete(key);
    }
}
