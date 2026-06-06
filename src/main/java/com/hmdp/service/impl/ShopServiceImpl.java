package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;

    @Override
    public Result querygetById(Long id) {
//        缓存穿透
        Shop shop = cacheClient
                .queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.SECONDS);

        //缓存击穿(互斥锁)
        //Shop shop = queryWithMutex(id);

        //缓存击穿（逻辑过期）
//        Shop shop = cacheClient
//                .queryWithLogicalExpire(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.SECONDS);

        if(shop==null){
            return Result.error("店铺不存在");
        }
        //返回
        return Result.success(shop);
    }

    /*
    //缓存穿透
    public Shop queryWithPassThrough(Long id){
        String key=CACHE_SHOP_KEY + id;
        //在Redis里查询
        log.info("开始在Redis中查询：{}",id);
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //存在，返回
        if(StrUtil.isNotBlank(shopJson)){
            log.info("Redis中查询成功！");
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //判断是否为空值，前面已经将有值的情况讨论了，剩下的不是空值就是null
        if(shopJson!=null){
            return null;
        }
        //不存在，在sql里查询
        log.info("未在Redis中查询到，将前往数据库查询。。。");
        Shop shop = getById(id);
        //不存在，返回错误信息,为防止缓存穿透，将信息保存在Redis
        if(shop==null){
            log.info("店铺不存在");
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //存在，将信息保存到Redis里
        log.info("数据库中查询成功！");
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //返回
        return shop;
    }
     */

    /*
    //缓存击穿(互斥锁)
    public Shop queryWithMutex(Long id){
        String key=CACHE_SHOP_KEY + id;
        //1.在Redis里查询
        log.info("开始在Redis中查询：{}",id);
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.存在，返回
        if(StrUtil.isNotBlank(shopJson)){
            log.info("Redis中查询成功！");
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //3.判断是否为空值
        if(shopJson!=null){
            return null;
        }
        //4.缓存重建
        //4.1获取互斥锁
        String lockKey=LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean islock = tryLock(lockKey);
            //4.2判断是否获取成功
            if(!islock){
                //4.3失败，休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //5成功，在sql里查询
            log.info("未在Redis中查询到，将前往数据库查询。。。");
            shop = getById(id);
            //不存在，返回错误信息
            if(shop==null){
                log.info("店铺不存在");
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            //6.存在，将信息保存到Redis里
            log.info("数据库中查询成功！");
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            //7.释放互斥锁
            unLock(lockKey);
        }
        //8.返回
        return shop;
    }

     */

    /*
    private static final ExecutorService CACHE_REBUID_EXECUTOR = Executors.newFixedThreadPool(10);//线程池

    //缓存击穿（逻辑过期）
    public Shop queryWithLogicalExpire(Long id){
        String key=CACHE_SHOP_KEY + id;
        //1.在Redis里查询
        log.info("开始在Redis中查询：{}",id);
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.不存在，返回
        if(StrUtil.isBlank(shopJson)){
            return null;
        }
        //3.存在，反序列化JSON
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //4.判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //5.未过期，直接返回店铺信息
            return shop;
        }
        //6.过期，缓存重建
        //6.1获取互斥锁
        String lockKey=LOCK_SHOP_KEY + id;
        boolean lock = tryLock(lockKey);
        //6.2判断是否取锁成功
        if(lock){
            //6.3.成功，开启独立线程，缓存重建
            CACHE_REBUID_EXECUTOR.submit(()->{
                //重建缓存
                try {
                    this.saveShop2Redis(id,20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //释放
                    unLock(lockKey);
                }
            });
        }
        //6.4.返回过期的商铺信息
        return shop;
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }

    private void  unLock(String key){
        stringRedisTemplate.delete(key);
    }

    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
        //1.查询店铺
        Shop shop = getById(id);
        Thread.sleep(200);
        //2.封装逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }
    */

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id=shop.getId();
        if(id==null){
            return Result.error("店铺id不能为空");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+id);
        return Result.success();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1.判断有无x,y（是否按坐标查寻）
        if (x == null || y == null) {
            // 不需要坐标查询
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.success(page.getRecords());
        }
        // 2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        // 3.查询redis，按照距离排序、分页,结果：typeId,distance
        String key = SHOP_GEO_KEY +  typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        // 4.解析出id
        if (results == null) {
            return Result.success();
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        // 4.1.截取出from-end部分
        ArrayList<Object> ids = new ArrayList<>(list.size());
        Map<String,Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            // 4.2.获取店铺id
            String id = result.getContent().getName();
            ids.add(Long.valueOf(id));
            // 4.3.获取距离
            Distance distance = result.getDistance();
            distanceMap.put(id,distance);
        });
        // 5.根据id查shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        // 6.返回
        return Result.success(shops);
    }
}
