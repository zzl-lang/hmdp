package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;


@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //发送验证码
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 基于Redis发送验证码
        //1.校验手机号是否合格
        if(RegexUtils.isPhoneInvalid(phone)){
            //不合格
            return Result.fail("手机号码格式不正确");
        }
        //2.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        //3.保存验证码到Redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //4.发送验证码
        log.info("发送短信验证码为: "+code);
        //返回
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.验证手机号码格式正确性
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            //不合格
            return Result.fail("手机号码格式不正确");
        }
        //2.手机号码正确，校验验证码是否正确
        String code = loginForm.getCode();
        //从Redis中获取 cacheCode
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY).toString();
        if(cacheCode==null || !cacheCode.equals(code)){
            return Result.fail("验证码错误");
        }
        //3.验证码一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();
        if(user == null){
            //4.用户不存在创建新用户
            user = creatOneWithPhone(phone);
        }
        //6.保存用户到Redis
        //6.1 构建随机token为key存储用户数据
        String token = UUID.randomUUID(true).toString();
        //6.2 构建Map
        UserDTO userDTO = BeanUtil.copyProperties(user,UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        //6.3 存入Redis
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
        //7.设置token有效期
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.MINUTES);
        //8.返回token
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        //1. 获取当前用户信息
        Long userId = UserHolder.getUser().getId();
        //2. 获取日期
        LocalDateTime now = LocalDateTime.now();
        //3. 拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        //4. 获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //5. 写入Redis setbit key offset 1
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth-1,true);
        return Result.ok();
    }

    @Override
    public Result signCount() {

        //1. 获取当前用户信息
        Long userId = UserHolder.getUser().getId();
        //2. 获取日期
        LocalDateTime now = LocalDateTime.now();
        //3. 拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        //4. 获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //5.获取本月截止今天为止的所有签到记录
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key, BitFieldSubCommands.create()
                        .get(BitFieldSubCommands
                                .BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if(result==null || result.isEmpty()){
            return Result.ok(0);
        }
        Long num = result.get(0);
        if(num == null || num ==0){
            return Result.ok(0);
        }
        // 6.循环遍历
        int count = 0;
        while(true){
            // 7.让这个数组与1做与运算，得到数字的最后一个bit位
            if((num & 1) == 0){
                // 如果数字为0 未签到
                break;
            }else{
                // 如果为数字1 已签到，计数器加1
                count++;
            }
            num >>>= 1;
            // 把数字右移一位抛弃最后一位
        }
        return Result.ok(count);
    }

    private User creatOneWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(5));
        //5.保存新用户到数据库
        save(user);
        return user;
    }
}
