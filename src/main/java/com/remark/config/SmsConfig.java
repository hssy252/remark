package com.remark.config;


import com.remark.properties.SMSProperties;
import com.remark.utils.SMSUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 功能简述
 *
 * @author hssy
 * @version 1.0
 */
@Configuration
public class SmsConfig {

    @Bean
    @ConditionalOnMissingBean
    public SMSUtils smsUtils(SMSProperties smsProperties) {
        return new SMSUtils(smsProperties.getAccessKey(), smsProperties.getAccessSecret());
    }

}
