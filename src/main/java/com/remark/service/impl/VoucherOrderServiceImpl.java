package com.remark.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.remark.dto.Result;
import com.remark.entity.SeckillVoucher;
import com.remark.entity.VoucherOrder;
import com.remark.mapper.VoucherOrderMapper;
import com.remark.service.ISeckillVoucherService;
import com.remark.service.IVoucherOrderService;
import com.remark.utils.RedisIdWorker;
import com.remark.utils.UserHolder;
import java.time.LocalDateTime;
import javax.annotation.Resource;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {


    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.检查优惠券是否开始
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        if (beginTime.isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀活动尚未开始");
        }
        //2.是否结束
        LocalDateTime endTime = seckillVoucher.getEndTime();
        if (endTime.isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀活动已结束");
        }
        //3.库存是否足够
        Integer stock = seckillVoucher.getStock();
        if (stock < 1) {
            return Result.fail("秒杀券已被抢尽");
        }
        Long userId = UserHolder.getUser().getId();

//        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        simpleRedisLock.tryLock(12);
//        try {
//            Thread.sleep(1000);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
//        simpleRedisLock.unLock();

        //如果不加intern，每一次toString其实产生的是一个新的对象
        //锁加在这里是因为，如果锁加在方法内部，那么锁解开了事务才会提交，所以锁的范围要扩大
        synchronized (userId.toString().intern()) {
            //获取事务代理对象,因为事务注解只有该方法为代理对象调用时才会生效
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.seckillResult(voucherId);
        }

    }

    @Transactional
    public Result seckillResult(Long voucherId) {
        //查询是否有购买记录，确保一人一单
        Long userId = UserHolder.getUser().getId();
        long orderId;

        int count = query().eq("user_id", userId).count();
        if (count > 0) {
            return Result.fail("您已经购买过");
        }

        //4.扣减库存
        boolean success = seckillVoucherService.update().eq("voucher_id", voucherId).setSql("stock=stock-1")
            .gt("stock", 0).update();
        if (!success) {
            return Result.fail("库存不足");
        }
        //5.生成订单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(voucherId);
        orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        save(voucherOrder);
        return Result.ok(orderId);

    }

}


