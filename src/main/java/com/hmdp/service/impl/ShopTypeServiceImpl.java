package com.hmdp.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Slf4j
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        String key = CACHE_SHOP_TYPE_KEY;
        //在Redis里查询
        log.info("开始在Redis中查询");
        List<String> shopTypeJsonList = stringRedisTemplate.opsForList().range(key,0,-1);
        //存在，返回
        if(shopTypeJsonList != null && !shopTypeJsonList.isEmpty()){
            log.info("Redis中查询成功！");
            // JSON字符串转对象 排序后返回
            List<ShopType> shopTypes = shopTypeJsonList.stream()
                    .map(jsonStr -> JSONUtil.toBean(jsonStr, ShopType.class))
                    .collect(Collectors.toList());
            // 如果数据库查询时已排序且写入时保持了顺序，此处可以不用再排序
            Collections.sort(shopTypes, Comparator.comparingInt(ShopType::getSort));
            return Result.success(shopTypes);
        }
        //不存在，在sql里查询
        log.info("未在Redis中查询到，将前往数据库查询。。。");
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        //不存在，返回错误信息
        if(shopTypes==null){
            log.info("店铺类型不存在");
            return Result.error("店铺类型不存在");
        }
        // 5. 存在， 写入Redis
        List<String> shopTypesJson = shopTypes.stream()
                .map(shopType -> JSONUtil.toJsonStr(shopType))
                .collect(Collectors.toList());
        stringRedisTemplate.opsForList().rightPushAll(key, shopTypesJson);
        //返回
        return Result.success(shopTypes);
    }
}
