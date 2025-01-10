package com.example.arsu.service;

import com.example.arsu.model.User;
import com.example.arsu.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.TimeUnit;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public User registerUser(User user) {
        if (userRepository.findByUsername(user.getUsername()) != null) {
            throw new RuntimeException("Username already exists");
        }
        if (userRepository.findByEmail(user.getEmail()) != null) {
            throw new RuntimeException("Email already exists");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setCreatedAt(new Date());
        user.setUpdatedAt(new Date());
        User savedUser = userRepository.save(user);
        cacheUser(savedUser);
        return savedUser;
    }

    public User loginUser(String username, String password) {
        User user = getCachedUser(username);
        if (user == null) {
            user = userRepository.findByUsername(username);
            if (user != null) {
                cacheUser(user);
            }
        }
        if (user != null && passwordEncoder.matches(password, user.getPassword())) {
            return user;
        }
        return null;
    }

    private void cacheUser(User user) {
        redisTemplate.opsForValue().set(user.getUsername(), user, 1, TimeUnit.HOURS);
    }

    private User getCachedUser(String username) {
        return (User) redisTemplate.opsForValue().get(username);
    }
}