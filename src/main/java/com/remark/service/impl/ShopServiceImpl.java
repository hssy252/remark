package com.remark.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.remark.dto.Result;
import com.remark.entity.Shop;
import com.remark.mapper.ShopMapper;
import com.remark.service.IShopService;
import com.remark.utils.RedisConstants;
import com.remark.utils.RedisData;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    public Result queryById(Long id) {

        //缓存穿透
        //Shop shop=queryWithPassThrough(id);

        //互斥锁解决缓存击穿
        Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存击穿
        //Shop shop = queryWithLogicExpire(id);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);

    }

    /**
     * 逻辑过期处理热点key缓存击穿的问题
     *
     * @param id
     * @return
     */
    public Shop queryWithLogicExpire(Long id) {
        //1.查询redis
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopStr = stringRedisTemplate.opsForValue().get(key);

        //2.没有命中则返回none
        if (StrUtil.isBlank(shopStr)) {
            return null;
        }
        //3.如果命中则判断是否过期
        //这里的实际上
        RedisData redisData = JSONUtil.toBean(shopStr, RedisData.class);
        //注意，这里的类型为JsonObject，不能直接强转
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        boolean isExpired = LocalDateTime.now().isAfter(redisData.getExpireTime());
        //4.未过期则直接返回信息
        if (!isExpired) {
            return shop;
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
                return JSONUtil.toBean((JSONObject) bean.getData(), Shop.class);
            } else {
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    try {
                        //重建缓存
                        this.saveHot2Redis(id, 20L);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        unLock(lockKey);
                    }
                });
            }
        }
        //5.1.2 获取失败则直接返回旧的信息
        return shop;
    }

    //利用互斥锁防止缓存击穿的查询
    public Shop queryWithMutex(Long id) {
        //1.查询redis
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopStr = stringRedisTemplate.opsForValue().get(key);

        //如果不为空则说明查到了
        if (StrUtil.isNotBlank(shopStr)) {
            return JSONUtil.toBean(shopStr, Shop.class);
        }

        //如果不为null则为空字符串，说明是空对象
        if (shopStr != null) {
            return null;
        }

        //如果为null，则未命中过
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            //命中失败，获取互斥锁
            boolean isLock = tryLock(lockKey);
            //获取失败则等待，然后重新查询redis
            if (!isLock) {
                Thread.sleep(50);

                return queryWithMutex(id);
            }
            //获取互斥锁成功则重建缓存
            shop = getById(id);

            //3.数据库没查到则返回不存在
            if (shop == null) {
                //防止缓存穿透，存入空对象
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //4.数据库查到了就写入缓存再返回
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unLock(lockKey);
        }
        return shop;

    }

    //防止缓存穿透的查询
    public Shop queryWithPassThrough(Long id) {
        //1.查询redis
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopStr = stringRedisTemplate.opsForValue().get(key);

        //2.没有则查询数据库
        if (StrUtil.isBlank(shopStr)) {
            if (Objects.equals(shopStr, "")) {
                return null;
            }
            Shop shop = getById(id);
            //3.数据库没查到则返回不存在
            if (shop == null) {
                //防止缓存穿透，存入空对象
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //4.数据库查到了就写入缓存再返回
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return shop;
        }

        //5.redis查到了直接返回
        return JSONUtil.toBean(shopStr, Shop.class);
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

    @Override
    @Transactional//控制数据库和缓存的一致性
    public Result update(Shop shop) {

        //1.判断id
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }

        //2.更新数据库
        updateById(shop);

        //3.删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);

        return Result.ok();

    }

    /**
     * 预加载热点信息
     *
     * @param shopId
     * @param expireTime
     */
    public void saveHot2Redis(Long shopId, Long expireTime) {
        //1.获取shop信息
        Shop shop = getById(shopId);
        //2.转化为RedisData
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
        //3.存入redis，预加载热点数据
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + shopId, JSONUtil.toJsonStr(redisData));
    }
}
