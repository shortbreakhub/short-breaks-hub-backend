package com.shortbreakshub.service;

import com.shortbreakshub.dto.LoginRequest;
import com.shortbreakshub.dto.LoginResponse;
import com.shortbreakshub.dto.MeResponse;
import com.shortbreakshub.dto.RenewTokenReq;
import com.shortbreakshub.model.User;
import com.shortbreakshub.repository.UserRepository;
import com.shortbreakshub.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@RequiredArgsConstructor
@Service
public class AuthService {
    private final UserRepository users;
    private final JwtService jwt;
    private final PasswordEncoder passwordEncoder;
    private final HttpServletRequest request;
    private final EmailVerificationService token;
    private final EmailService emailService;

    @Value("${app.public-base-url}")
    private String publicBaseUrl;



    public LoginResponse login(LoginRequest req) {
        var user = users.findByEmail(req.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        String token = jwt.generateToken(user.getId(), user.getEmail(), user.getDisplayName());
        return new LoginResponse(token, user.getId(), user.getEmail(), user.getDisplayName(),user.isEmailVerified());
    }

    public MeResponse meFromToken(String bearer) {
        if (bearer == null || !bearer.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing token");
        }

        Long userId = (Long) request.getAttribute("authUserId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }

        var user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        return MeResponse.from(user);
    }

    public String meRenewToken(RenewTokenReq req) {
        return jwt.generateToken(req.userId(),  req.email(), req.displayName());
    }

    public void resendVerificationEmail(User user) {
        if (user.isEmailVerified()) {
            throw new IllegalStateException("Email already verified");
        }
        String confirmLink = UriComponentsBuilder
                .fromHttpUrl(publicBaseUrl)
                .path("/api/auth/verify-email")
                .queryParam("token", token.createToken(user).getToken())
                .toUriString();

        emailService.sendConfirmationEmail(user.getEmail(),user.getDisplayName(),confirmLink);
    }

}
