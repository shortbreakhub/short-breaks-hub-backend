package com.shortbreakshub.service;
import com.shortbreakshub.dto.MeResponse;
import com.shortbreakshub.dto.UpdateAvatarReq;
import com.shortbreakshub.dto.UpdateMeReq;
import com.shortbreakshub.model.User;
import com.shortbreakshub.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Service
public class UserService {
    private final UserRepository repo;
    private final BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
    private final DraftCoverUploadService coverUploads;
    private final EmailService emailService;
    private final EmailVerificationService token;

    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    public UserService(UserRepository repo, DraftCoverUploadService coverUploads,
                       EmailService emailService, EmailVerificationService token) {
        this.repo = repo;
        this.coverUploads = coverUploads;
        this.emailService = emailService;
        this.token = token;
    }

    public User register(String email, String password, String displayName, String location, String bio, Integer adults, Integer children) {
        if (repo.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email Already Exists");
        }
        User u = new User(email, enc.encode(password), displayName,location, bio, adults, children);
        repo.save(u);
        String confirmLink = UriComponentsBuilder
                .fromHttpUrl(publicBaseUrl)
                .path("/api/auth/verify-email")
                .queryParam("token", token.createToken(u).getToken())
                .toUriString();
        emailService.sendConfirmationEmail(u.getEmail(),u.getDisplayName(),confirmLink);
        return u;
    }

    public User getUserById(Long id) {
        return repo.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found")
        );
    }

    @Transactional
    public MeResponse updateOwnProfileById(Long userId, UpdateMeReq req) {
        var user = repo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (req.location() != null)  user.setLocation(req.location());
        if (req.bio() != null)       user.setBio(req.bio());
        if (req.adults() != null)    user.setAdults(req.adults());
        if (req.children() != null)  user.setChildren(req.children());
        if (req.displayName() != null)  user.setDisplayName(req.displayName());
        if (req.currency() != null)  user.setCurrency(req.currency());
        repo.save(user);
        return MeResponse.from(user);
    }

    @Transactional
    public MeResponse updateOwnAvatarById(Long userId, UpdateAvatarReq req) throws IOException {
        var user = repo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        if (req.avatarUrl() == null || req.avatarUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Avatar URL required");
        }
        coverUploads.validateAssignment(userId, req.avatarUrl(), user.getAvatarUrl());
        user.setAvatarUrl(req.avatarUrl());
        // Preserve all previous assets; avatar replacement must not bypass cover retention.
        repo.save(user);
        return MeResponse.from(user);
    }


}
