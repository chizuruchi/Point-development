package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWoker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.*;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private CacheClient cacheClient;

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedisIdWoker redisIdWoker;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void tsetIdWoker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = () ->{
            for(int i = 0; i < 100; i++){
                long id = redisIdWoker.nextId("order");
                System.out.println("id= "+ id);
            }
            latch.countDown();
        };

        long begin = System.currentTimeMillis();
        for(int i = 0; i < 300; i++){
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("耗时： "+ (end-begin));

        es.shutdown();
    }

    @Test
    public void tset_cacheClient(){
        Shop shop = shopService.getById(1L);

        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY+1L, shop, CACHE_SHOP_TTL, TimeUnit.MINUTES);
    }

}
