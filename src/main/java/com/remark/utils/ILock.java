package com.remark.utils;

/**
 * 功能简述
 *
 * @author hssy
 * @version 1.0
 */
public interface ILock {

    /**
     * 获取分布式互斥锁
     * @param timeoutSec 互斥锁过期时间
     * @return 是否加锁成功
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unLock();

}
