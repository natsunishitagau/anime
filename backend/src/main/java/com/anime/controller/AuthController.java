package com.anime.controller;

import com.anime.dto.UserDto;
import com.anime.dto.request.LoginRequest;
import com.anime.dto.request.RegisterRequest;
import com.anime.dto.response.ApiResponse;
import com.anime.entity.User;
import com.anime.repository.UserRepository;
import com.anime.security.JwtUtils;
import com.anime.service.UserSettingsService;
import com.anime.util.SensitiveWordFilter;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final SensitiveWordFilter sensitiveWordFilter;
    private final UserSettingsService userSettingsService;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtils jwtUtils, 
                         SensitiveWordFilter sensitiveWordFilter, UserSettingsService userSettingsService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.sensitiveWordFilter = sensitiveWordFilter;
        this.userSettingsService = userSettingsService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserDto>> register(@Valid @RequestBody RegisterRequest request) {
        if (!sensitiveWordFilter.validate(request.getUsername())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("存在违规字符"));
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Username already taken"));
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Email already registered"));
        }

        if (request.getUsername().length() < 3 || request.getUsername().length() > 30) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Username must be 3-30 characters"));
        }

        if (request.getPassword().length() < 6) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Password must be at least 6 characters"));
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setAvatarUrl("/src/assets/avatars/default.svg");

        User savedUser = userRepository.save(user);

        userSettingsService.createSettingsForUser(savedUser.getId());

        UserDto userDto = new UserDto(savedUser.getId(), savedUser.getUsername(), savedUser.getEmail(), savedUser.getSignature(), savedUser.getAvatarUrl(), savedUser.getRole());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Registration successful", userDto));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername()).orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid credentials"));
        }

        String token = jwtUtils.generateToken(user.getId(), user.getUsername());
        UserDto userDto = new UserDto(user.getId(), user.getUsername(), user.getEmail(), user.getSignature(), user.getAvatarUrl(), user.getRole());

        LoginResponse response = new LoginResponse(token, userDto);
        return ResponseEntity.ok(new ApiResponse<>(true, "Login successful", response));
    }

    @GetMapping("/verify")
    public ResponseEntity<ApiResponse<UserDto>> verify(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("No token provided"));
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        if (!jwtUtils.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid token"));
        }

        Long userId = jwtUtils.getUserIdFromToken(token);
        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("User not found"));
        }

        UserDto userDto = new UserDto(user.getId(), user.getUsername(), user.getEmail(), user.getSignature(), user.getAvatarUrl(), user.getRole());
        return ResponseEntity.ok(new ApiResponse<>(true, "Token valid", userDto));
    }

    static class LoginResponse {
        private String token;
        private UserDto user;

        public LoginResponse(String token, UserDto user) {
            this.token = token;
            this.user = user;
        }

        public String getToken() { return token; }
        public UserDto getUser() { return user; }
    }
}