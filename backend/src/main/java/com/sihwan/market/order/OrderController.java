package com.sihwan.market.order;

import com.sihwan.market.order.OrderDtos.CreateRequest;
import com.sihwan.market.order.OrderDtos.CreatedResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateRequest req) {
        return service.create(Long.parseLong(jwt.getSubject()), req);
    }
}
