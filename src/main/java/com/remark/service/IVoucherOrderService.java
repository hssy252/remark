package com.remark.service;

import com.remark.dto.Result;
import com.remark.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {


    Result seckillVoucher(Long voucherId);

    Result seckillResult(Long voucherId);

    void asyncSeckill(VoucherOrder order);
}
