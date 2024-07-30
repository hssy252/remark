package com.remark;

import com.remark.service.impl.ShopServiceImpl;
import com.remark.utils.SimpleRedisLock;
import java.util.concurrent.TimeUnit;
import javax.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest(classes = RemarkApplication.class)
class RemarkApplicationTests {

    @Autowired
    private ShopServiceImpl service;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private SimpleRedisLock redisLock = new SimpleRedisLock("order:1", null);

    @Autowired
    private RedissonClient redisson;

    @Test
    public void testHotKey() {
        service.saveHot2Redis(1L, 6L);
    }

    @Test
    public void testRedisLock() {
        redisLock.setStringRedisTemplate(stringRedisTemplate);
        redisLock.tryLock(20);
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        redisLock.unLock();
    }

    @Test
    public void testRedisson() throws InterruptedException {
        RLock lock = redisson.getLock("distributed");
        boolean tried = lock.tryLock(1, 10, TimeUnit.SECONDS);
        if (tried) {
            try {
                System.out.println("获取到锁");
            } finally {
                lock.unlock();
            }
        }

    }
}
