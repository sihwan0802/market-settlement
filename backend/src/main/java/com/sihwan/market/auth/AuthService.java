package com.sihwan.market.auth;

import com.sihwan.market.auth.AuthDtos.LoginRequest;
import com.sihwan.market.auth.AuthDtos.PasswordChangeRequest;
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

    @Transactional
    public void changePassword(long memberId, PasswordChangeRequest req) {
        Member m = verifiedMember(memberId, req.currentPassword());
        if (req.currentPassword().equals(req.newPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "새 비밀번호가 기존과 같습니다");
        }
        members.updatePassword(m.id(), encoder.encode(req.newPassword()));
    }

    @Transactional
    public void withdraw(long memberId, String password) {
        Member m = verifiedMember(memberId, password);
        // 미정산 금액과 진행 중 주문을 확인하는 판매자 탈퇴 규칙은 아직 없어서 막아 둔다
        if ("SELLER".equals(m.role()) || "ADMIN".equals(m.role())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "판매자와 관리자는 탈퇴할 수 없습니다");
        }
        members.withdraw(m.id());
    }

    private Member verifiedMember(long memberId, String password) {
        return members.findActiveById(memberId)
                .filter(found -> encoder.matches(password, found.passwordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "비밀번호가 올바르지 않습니다"));
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
