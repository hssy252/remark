package com.remark.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.remark.dto.LoginFormDTO;
import com.remark.dto.Result;
import com.remark.dto.UserDTO;
import com.remark.entity.User;
import com.remark.mapper.UserMapper;
import com.remark.service.IUserService;
import com.remark.utils.RedisConstants;
import com.remark.utils.RegexUtils;
import com.remark.utils.SMSUtils;
import com.remark.utils.SystemConstants;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private SMSUtils smsUtils;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.验证手机验证码格式
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        //2.生成验证码
        String code = RandomUtil.randomNumbers(6);

        //3.保存到session里
        //session.setAttribute("code",code);

        //3.保存到redis当中，为了解决session共享问题，set key value ex 120
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //4.发送短信验证码
        smsUtils.sendMessage("西湖论剑", "SMS_465314318", phone, code);

        //5.结束
        return Result.ok();

    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        //2.校验验证码
//        Object cacheCode = session.getAttribute("code");
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);

        //2.从redis当中获取校验码
        if (cacheCode == null || !cacheCode.equals(loginForm.getCode())) {
            return Result.fail("验证码错误");
        }

        //3.查询数据库是否有该用户
        User user = query().eq("phone", phone).one();
        if (user == null) {
            //4.不存在则创建用户并保存到数据库
            user = createUserWithPhone(phone);
        }

        //5.保存到session中
//        session.setAttribute("user",BeanUtil.copyProperties(user, UserDTO.class));

        //5.保存到redis当中，key为生成的token，并且要返回给前端，User用Hash格式存储

        //生产token
        String token = UUID.randomUUID().toString(true);

        //将User转换为HashMap
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> map = BeanUtil.beanToMap(userDTO, new HashMap<>(),
            CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        //存入redis
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, map);

        //设置key的有效期，模仿session过期时间
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        //返回token
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);

        return user;
    }
}
