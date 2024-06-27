package com.remark.service.impl;

import cn.hutool.json.JSONUtil;
import com.remark.dto.Result;
import com.remark.entity.ShopType;
import com.remark.mapper.ShopTypeMapper;
import com.remark.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.remark.utils.RedisConstants;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryList() {

        //1，查询redis缓存
        List<String> shopStrList = stringRedisTemplate.opsForList().range(RedisConstants.CACHE_SHOP_TYPE, 0, -1);
        if (shopStrList == null||shopStrList.size()==0){
            //2.没有缓存，查询数据库
            List<ShopType> shopTypeList = query().orderByAsc("sort").list();

            //3.写入缓存
            shopTypeList.stream().map((JSONUtil::toJsonStr)).forEach(string -> stringRedisTemplate.opsForList().rightPush(RedisConstants.CACHE_SHOP_TYPE,string));
            return Result.ok(shopTypeList);
        }

        //4.直接返回缓存
        List<ShopType> shopTypeList = shopStrList.stream().map(string -> JSONUtil.toBean(string, ShopType.class)).collect(Collectors.toList());
        return Result.ok(shopTypeList);
    }
}
