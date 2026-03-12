package com.emby.mvp.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.emby.mvp.common.ApiResponse;
import com.emby.mvp.common.BizException;
import com.emby.mvp.dto.LoginRequest;
import com.emby.mvp.dto.LoginResponse;
import com.emby.mvp.entity.User;
import com.emby.mvp.mapper.UserMapper;
import com.emby.mvp.security.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()).last("limit 1"));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BizException(4011, "用户名或密码错误");
        }
        String token = jwtUtil.generate(user.getId(), user.getUsername(), user.getRole());
        return ApiResponse.ok(new LoginResponse(token, user.getId(), user.getUsername(), user.getRole()));
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        User user = userMapper.selectById(userId);
        if (user == null) throw new BizException(4041, "user not found");
        return ApiResponse.ok(Map.of("id", user.getId(), "username", user.getUsername(), "role", user.getRole()));
    }
}
