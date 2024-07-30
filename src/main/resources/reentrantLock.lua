---
--- Generated by EmmyLua(https://github.com/EmmyLua)
--- Created by 13759.
--- DateTime: 2024/7/30 17:27
---

--- 判断锁是否存在
if (redis.call(exists,KEY[1])==0) then
    --- 不存在就加锁
    redis.call(hset,KEY[1],ARGV[1],'1');
    --- 设置过期时间
    redis.call(expire,KEY[1],ARGV[2]);
    --- 返回结果
    return 1;
end;
--- 存在则判断是否是自己的
if (redis.call(hexists,KEY[1],ARGV[1]==1)) then
    --- 是自己的就加加
    redis.call(hincrby,KEY[1],ARGV[1],'1');
    ---- 设置有效期
    redis.call(expire,KEY[1],ARGV[2]);
    --- 返回结果
    return  1;
end;
--- 都不是代表获取锁失败
return 0;


--- 不是自己的就返回