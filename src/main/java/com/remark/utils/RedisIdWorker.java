package com.remark.utils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 功能简述
 *
 * @author hssy
 * @version 1.0
 */
@Component
public class RedisIdWorker {

    /**
     * 开始的时间戳
     */
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    /**
     * 左位移的位数
     */
    private static final int BITS_NUM = 32;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix) {
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowEpochSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowEpochSecond - BEGIN_TIMESTAMP;

        //2.生成序列号
        //2.1.获取当前日期
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //2.2.自增长
        //这里key要加上天数是因为如果只用一个key来自增生成序列号，redis单个key的自增是有上限的，而且序列号只需要32喂，有超过的风险
        //而且key加上日期后，则该天数对应的key的自增量就是下单数
        long count = stringRedisTemplate.opsForValue().increment(RedisConstants.INR_PREFIX + keyPrefix + ":" + date);

        //3.拼接并返回
        return timestamp << BITS_NUM | count;
    }

}
