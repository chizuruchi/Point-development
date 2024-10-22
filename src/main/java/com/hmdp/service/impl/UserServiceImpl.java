package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
//        1.检验手机号码
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2. 手机号码不合法，返回错误信息
            return Result.fail("手机号码格式错误！");
        }
//        3. 生成验证码
        String code = RandomUtil.randomNumbers(6);
        log.debug("code:{}",code);

//        4.保存验证码到session中
//        session.setAttribute(phone,code);
//        session.setMaxInactiveInterval(300);

//        4.修改为保存到redis中,记得设置有效期
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
//        0.检验手机号码
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            // 2. 手机号码不合法，返回错误信息
            return Result.fail("手机号码格式错误！");
        }
//        1.取出验证码，检查是否与session中的为同一个验证码
//        Object catchcode = session.getAttribute(loginForm.getPhone());

        // TODO 1.修改为在redis中取出验证码
        String catchcode = stringRedisTemplate.opsForValue().get("login:code:"+loginForm.getPhone());
        String code = loginForm.getCode();
//        先判断catchcode是否为null，防止NullPointerException
        if(catchcode == null || !catchcode.toString().equals(code)){
            // 2.验证码错误，返回错误信息
            return Result.fail("验证码错误！");
        }
//        if (loginForm.getCode() != session.getAttribute("code")) 判断引用对象是否一致，不判断内容是否一致

//        3.根据手机号查询用户
        User user = query().eq("phone",loginForm.getPhone()).one();

//        4. 用户不存在，创建用户，保存用户到数据库
        if (user == null){
            user = crateUserWithPhone(loginForm.getPhone());
        }

//        TODO 5.改为存储到redis中
//        5.1 将user类转换为hashmap类型
        Map<String, String> userDTO = new HashMap<>();
        userDTO.put("id",user.getId().toString());
        userDTO.put("nickname",user.getNickName());
        userDTO.put("icon",user.getIcon());

//        5.3 使用uuid库随机生成token
        String token = UUID.randomUUID().toString();

//        5.2 将用户保存到redis中
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userDTO);
        stringRedisTemplate.expire(LOGIN_USER_KEY + token,LOGIN_USER_TTL,TimeUnit.MINUTES);

//        5.保存用户到session
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        return Result.ok(token);
    }

    private User crateUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
//        保存到数据库
        save(user);
        return user;
    }
}
