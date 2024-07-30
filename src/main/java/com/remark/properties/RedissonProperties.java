package com.remark.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 功能简述
 *
 * @author hssy
 * @version 1.0
 */
@Component
@ConfigurationProperties(prefix = "redisson")
@Data
public class RedissonProperties {

    private String password;

    private String url;

}
