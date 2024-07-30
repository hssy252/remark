package com.remark.config;

import com.remark.properties.RedissonProperties;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 功能简述
 *
 * @author hssy
 * @version 1.0
 */
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redisson(RedissonProperties redissonProperties){
        //配置类
        Config config = new Config();
        //这里使用了单体模式，也可以使用userClusterServers()来配置集群地址
        config.useSingleServer().setAddress(redissonProperties.getUrl()).setPassword(redissonProperties.getPassword());
        return Redisson.create(config);
    }

}
