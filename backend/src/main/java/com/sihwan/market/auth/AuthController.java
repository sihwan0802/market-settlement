package com.sihwan.market.auth;

import com.sihwan.market.auth.AuthDtos.LoginRequest;
import com.sihwan.market.auth.AuthDtos.PasswordChangeRequest;
import com.sihwan.market.auth.AuthDtos.SignupRequest;
import com.sihwan.market.auth.AuthDtos.TokenResponse;
import com.sihwan.market.auth.AuthDtos.WithdrawRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/api/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public void signup(@Valid @RequestBody SignupRequest req) {
        auth.signup(req);
    }

    @PostMapping("/api/auth/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    @PutMapping("/api/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PasswordChangeRequest req) {
        auth.changePassword(Long.parseLong(jwt.getSubject()), req);
    }

    @PostMapping("/api/me/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody WithdrawRequest req) {
        auth.withdraw(Long.parseLong(jwt.getSubject()), req.password());
    }

    @GetMapping("/api/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("memberId", Long.parseLong(jwt.getSubject()), "role", jwt.getClaimAsString("role"));
    }
}
