package com.sihwan.market.refund;

import com.sihwan.market.refund.RefundDtos.RefundRequest;
import com.sihwan.market.refund.RefundDtos.RefundResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders/{orderId}/refunds")
public class RefundController {

    private final RefundService service;

    public RefundController(RefundService service) {
        this.service = service;
    }

    @PostMapping
    public RefundResponse refund(@AuthenticationPrincipal Jwt jwt, @PathVariable long orderId,
                                 @Valid @RequestBody RefundRequest req) {
        return service.refund(Long.parseLong(jwt.getSubject()), orderId, req);
    }
}
