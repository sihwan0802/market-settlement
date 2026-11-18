package com.sihwan.market.order;

import com.sihwan.market.order.OrderDtos.SellerLine;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seller/orders")
public class SellerOrderController {

    private final OrderQueryService query;

    public SellerOrderController(OrderQueryService query) {
        this.query = query;
    }

    @GetMapping
    public List<SellerLine> list(@AuthenticationPrincipal Jwt jwt,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        return query.sellerLines(Long.parseLong(jwt.getSubject()), page, size);
    }
}
