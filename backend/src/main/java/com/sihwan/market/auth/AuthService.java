package com.sihwan.market.auth;

import com.sihwan.market.auth.AuthDtos.LoginRequest;
import com.sihwan.market.auth.AuthDtos.SignupRequest;
import com.sihwan.market.auth.AuthDtos.TokenResponse;
import com.sihwan.market.member.Member;
import com.sihwan.market.member.MemberMapper;
import java.time.Duration;
import java.time.Instant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private static final Duration TOKEN_TTL = Duration.ofHours(1);

    private final MemberMapper members;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;

    public AuthService(MemberMapper members, PasswordEncoder encoder, JwtEncoder jwtEncoder) {
        this.members = members;
        this.encoder = encoder;
        this.jwtEncoder = jwtEncoder;
    }

    @Transactional
    public void signup(SignupRequest req) {
        boolean seller = "SELLER".equals(req.role());
        if (seller && (req.storeName() == null || req.storeName().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "판매자는 storeName이 필요합니다");
        }
        try {
            long memberId = members.insertMember(req.email(), encoder.encode(req.password()), req.name(), req.role());
            if (seller) {
                members.insertSeller(memberId, req.storeName());
            }
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다");
        }
    }

    public TokenResponse login(LoginRequest req) {
        Member m = members.findByEmail(req.email())
                .filter(found -> encoder.matches(req.password(), found.passwordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다"));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(m.id()))
                .claim("role", m.role())
                .issuedAt(now)
                .expiresAt(now.plus(TOKEN_TTL))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new TokenResponse(jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue());
    }
}
