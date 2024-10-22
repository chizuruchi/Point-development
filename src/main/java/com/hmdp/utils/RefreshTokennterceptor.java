package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

@Slf4j
public class RefreshTokennterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokennterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession();
//        1.改为从redis中取出用户信息
        String token = request.getHeader("Authorization");

        if(StrUtil.isBlank(token)){
            return true;
        }

        stringRedisTemplate.expire(LOGIN_USER_KEY+token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        Map<Object, Object> user = this.stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY+token);

//        1.session中获取用户信息
//        Object user = session.getAttribute("user");

//        2.判断用户是否存在
        if (user.isEmpty()){
            return true;
        }

//        4.用户存在，保存到ThreadLoacl中
        UserDTO userDTO = new UserDTO();
        userDTO.setId(Long.valueOf((String) user.get("id")));
        userDTO.setNickName((String) user.get("nickname"));
        userDTO.setIcon((String) user.get("icon"));

        UserHolder.saveUser(userDTO);
        log.debug("User saved in ThreadLocal: {}", UserHolder.getUser());

//        5.放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }

}
