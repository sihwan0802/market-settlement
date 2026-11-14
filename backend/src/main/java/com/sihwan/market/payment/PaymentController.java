package com.sihwan.market.payment;

import com.sihwan.market.payment.PaymentDtos.ConfirmRequest;
import com.sihwan.market.payment.PaymentDtos.ConfirmResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders/{orderId}/payments")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping
    public ConfirmResponse confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable long orderId,
                                   @Valid @RequestBody ConfirmRequest req) {
        return service.confirm(Long.parseLong(jwt.getSubject()), orderId, req.paymentId());
    }
}
