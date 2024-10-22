package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

// 用于生成订单id
@Component
public class RedisIdWoker {
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    private static final int COUNT_BITS = 32;

    private static StringRedisTemplate stringRedisTemplate;

    public RedisIdWoker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public static long nextId(String KeyPrefix){
        // 1. 生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSecond - BEGIN_TIMESTAMP;

        // 2. 生成序列号
        String Date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long increment = stringRedisTemplate.opsForValue().increment("icr:"+ KeyPrefix + ":" + Date);
        if(increment == 1L){
            stringRedisTemplate.expire("icr:"+ KeyPrefix + ":" + Date, 86400L, TimeUnit.SECONDS);
        }

        // 3. 拼接并返回
        return timeStamp << COUNT_BITS | increment;
    }
}

