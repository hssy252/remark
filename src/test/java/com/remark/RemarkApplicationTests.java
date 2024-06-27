package com.remark;

import com.remark.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RemarkApplicationTests {

    @Autowired
    private ShopServiceImpl service;

    @Test
    public void testHotKey(){
        service.saveHot2Redis(1L,6L);
    }
}
