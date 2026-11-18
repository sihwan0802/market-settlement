package com.sihwan.market.order;

import com.sihwan.market.order.OrderDtos.CreateRequest;
import com.sihwan.market.order.OrderDtos.CreatedResponse;
import com.sihwan.market.order.OrderDtos.OrderView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;
    private final OrderQueryService query;

    public OrderController(OrderService service, OrderQueryService query) {
        this.query = query;
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateRequest req) {
        return service.create(Long.parseLong(jwt.getSubject()), req);
    }

    @GetMapping
    public List<OrderView> list(@AuthenticationPrincipal Jwt jwt,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size) {
        return query.myOrders(Long.parseLong(jwt.getSubject()), page, size);
    }

    @GetMapping("/{id}")
    public OrderView get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return query.myOrder(Long.parseLong(jwt.getSubject()), id);
    }
}
