package com.remark.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.remark.entity.Shop;
import java.sql.Time;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class CacheClient {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public void set(String key,Object value,Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),unit.toSeconds(time));
    }

    public void setWithLogicExpire(String key,Object value,Long time,TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    public <R,ID> R queryWithPassThrough(String keyPrefix,ID id,Class<R> type,Long time, Function<ID,R> dbFallBack,TimeUnit unit) {
        //1.查询redis
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        //2.没有则查询数据库
        if (StrUtil.isBlank(json)) {
            if (Objects.equals(json, "")) {
                return null;
            }
            R r = dbFallBack.apply(id);
            //3.数据库没查到则返回不存在
            if (r == null) {
                //防止缓存穿透，存入空对象
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //4.数据库查到了就写入缓存再返回
            this.set(key,JSONUtil.toJsonStr(r),time,unit);
            return r;
        }

        //5.redis查到了直接返回
        return JSONUtil.toBean(json, type);
    }

    /**
     * 逻辑过期处理热点key缓存击穿的问题
     *
     * @param id
     * @return
     */
    public <R,ID> R queryWithLogicExpire(String keyPrefix,ID id,Class<R> type,Function<ID,R> dbFallback,Long time, TimeUnit unit) {
        //1.查询redis
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        //2.没有命中则返回none
        if (StrUtil.isBlank(json)) {
            return null;
        }
        //3.如果命中则判断是否过期
        //这里的实际上
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        //注意，这里的类型为JsonObject，不能直接强转
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        boolean isExpired = LocalDateTime.now().isAfter(redisData.getExpireTime());
        //4.未过期则直接返回信息
        if (!isExpired) {
            return r;
        }
        //5.过期则重新构建缓存
        //5.1 获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean lock = tryLock(lockKey);
        //5.1.1 获取成功则开启线程重建缓存，结束后释放锁并返回对象,这里要进行双锁检查,不然还是会冲击到数据库
        if (lock) {
            String doubleLock = stringRedisTemplate.opsForValue().get(key);
            RedisData bean = JSONUtil.toBean(doubleLock, RedisData.class);
            if (!LocalDateTime.now().isAfter(bean.getExpireTime())) {
                return JSONUtil.toBean((JSONObject) bean.getData(),type);
            } else {
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    try {
                        //重建缓存
                        this.setWithLogicExpire(key,JSONUtil.toJsonStr(redisData),time,unit);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        unLock(lockKey);
                    }
                });
            }
        }
        //5.1.2 获取失败则直接返回旧的信息
        return r;
    }

    //用redis的setnx操作模拟互斥锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "mutex", 10L, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //释放互斥锁
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }


}
