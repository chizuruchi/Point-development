package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        String shoptype_cache = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);

        if(StrUtil.isNotBlank(shoptype_cache)){
            return Result.ok(JSONUtil.toList(shoptype_cache,ShopType.class));
        }

        List<ShopType> typeList = query().orderByAsc("sort").list();

        if(typeList.isEmpty()){
            return Result.fail("无商户类型列表！");
        }

        stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(typeList));
        stringRedisTemplate.expire(CACHE_SHOP_TYPE_KEY,CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return Result.ok(typeList);
    }
}
