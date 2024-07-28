package com.remark.utils;

import cn.hutool.core.lang.UUID;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 功能简述
 *
 * @author hssy
 * @version 1.0
 */
public class SimpleRedisLock implements ILock {

    private String name;

    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = RedisConstants.LOCK_KEY;

    /**
     * 用来标识获得锁的线程，只有标识正确才能释放锁
     */
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    private static final DefaultRedisScript<Long> SCRIPT;

    static {
        SCRIPT = new DefaultRedisScript<>();
        SCRIPT.setLocation(new ClassPathResource("unLock.lua"));
        SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        //获取线程id，方便标识哪个线程得到了锁
        String threadId = ID_PREFIX + Thread.currentThread().getId();

        //获取互斥锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);

        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unLock() {
        stringRedisTemplate.execute(SCRIPT,
            Collections.singletonList(KEY_PREFIX + name),
            ID_PREFIX + Thread.currentThread().getId());
    }

    public void setStringRedisTemplate(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

//    @Override
//    public void unLock() {
//        //获取锁标识
//        String threadId = ID_PREFIX + Thread.currentThread().getId();
//
//        String lockFlag = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//
//        //释放锁
//        if (threadId.equals(lockFlag)){
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//    }
}
