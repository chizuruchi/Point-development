package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // 方法1:将任意]ava对象序列化为ison并存储在string类型的key中，并且可以设置TTL过期时间
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    // 方法2:将任意Java对象序列化为ison并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    // 方法3:根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
    /**
     * @param KeyPrefix key前缀
     * @param id id
     * @param type 返回值类型
     * @param dbFallback 查询数据库方法
     * @param time 键有效期
     * @param unit 有效期单位
     */
    public <R, ID> R queryWhihPassThrough(
            String KeyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = KeyPrefix + id;

        // 1.从redis缓存中查询
        String Cache = stringRedisTemplate.opsForValue().get(key);

        // 2. 判断是否存在
        if (StrUtil.isNotBlank(Cache)) {
            // 3.存在，返回数据
            return JSONUtil.toBean(Cache, type);
        }

        if (Cache != null && "".equals(Cache)) {
            return null;
        }

        // 4.不存在，去数据库中查询
        R r = dbFallback.apply(id);

        if (r == null) {
            // 5. 数据库中不存在，在redis中存储空缓存(缓存穿透)，并报错
            stringRedisTemplate.opsForValue().set(key, "", time, unit);
            return null;
        }

        // 6.将数据库中所查询到的数据储存到redis缓存中
        set(key, r, time, unit);

        // 7.返回数据
        return r;
    }

    // 方法4:根据指定的key查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题
    public <R, ID> R queryWhihLogicalExpire(
            String KeyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = KeyPrefix + id;

        // 1.从redis缓存中查询
        String Cache = stringRedisTemplate.opsForValue().get(key);

        //  2. 判断是否存在
        if(StrUtil.isBlank(Cache)){
            // 3. 不存在返回空值
            return null;
        }

        // 4. 命中先将json反序列化为对象
        RedisData redisData = JSONUtil.toBean(Cache, RedisData.class);
        R r1 = JSONUtil.toBean((JSONObject) redisData.getData(), type);

        // 5. 判断逻辑时间是否过期
        LocalDateTime expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())){
            // 6. 未过期，返回对象
            return r1;
        }

        // 7. 缓存击穿补充: 加互斥锁
        String lockKey = "lock:" + id;
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);

        if(!flag){
            // 获取锁失败，等待一段时间重试（例如 50 毫秒）
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return queryWhihLogicalExpire(KeyPrefix, id, type,  dbFallback, time, unit);  // 延迟后重新查询
        }

        try{
            // 8.不存在，去数据库中查询
            R r = dbFallback.apply(id);

            // 9. 将查询到的数据重新缓存到redis中
            setWithLogicalExpire(key, r, time, unit);

            // 10.返回数据
            return r;
        }finally {
            // 11. 确保只有获取锁的线程删除它
            String lockValue = stringRedisTemplate.opsForValue().get(lockKey);
            if ("1".equals(lockValue)) {
                stringRedisTemplate.delete(lockKey);  // 释放锁
            }
        }
    }
}
