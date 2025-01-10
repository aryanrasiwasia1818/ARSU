package com.example.arsu.controller;

import com.example.arsu.model.User;
import com.example.arsu.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public User registerUser(@RequestBody User user) {
        return userService.registerUser(user);
    }

    @PostMapping("/login")
    public User loginUser(@RequestBody User user) {
        User user1 = userService.loginUser(user.getUsername(), user.getPassword());
        if (user1 != null) {
            return user1;
        } else {
            throw new RuntimeException("Invalid username or password");
        }
    }
}