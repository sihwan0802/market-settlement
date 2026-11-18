package com.sihwan.market.order;

import com.sihwan.market.order.OrderDtos.ItemRow;
import com.sihwan.market.order.OrderDtos.ItemView;
import com.sihwan.market.order.OrderDtos.OrderRow;
import com.sihwan.market.order.OrderDtos.OrderView;
import com.sihwan.market.order.OrderDtos.SellerLine;
import com.sihwan.market.product.ProductMapper;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderQueryService {

    private final OrderMapper orders;
    private final ProductMapper products;

    public OrderQueryService(OrderMapper orders, ProductMapper products) {
        this.orders = orders;
        this.products = products;
    }

    public List<OrderView> myOrders(long buyerId, int page, int size) {
        int safeSize = clampSize(size);
        return withItems(orders.findByBuyer(buyerId, safeSize, Math.max(page, 0) * safeSize));
    }

    public OrderView myOrder(long buyerId, long orderId) {
        OrderRow row = orders.findOneByBuyer(orderId, buyerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "주문이 없습니다"));
        return withItems(List.of(row)).get(0);
    }

    public List<SellerLine> sellerLines(long memberId, int page, int size) {
        long sellerId = products.findSellerId(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "판매자 정보가 없습니다"));
        int safeSize = clampSize(size);
        return orders.findSellerLines(sellerId, safeSize, Math.max(page, 0) * safeSize);
    }

    // 주문마다 항목을 따로 조회하지 않고 한 번에 가져와 묶는다
    private List<OrderView> withItems(List<OrderRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<ItemView>> items = orders.findItemsByOrderIds(rows.stream().map(OrderRow::id).toList())
                .stream().collect(Collectors.groupingBy(ItemRow::orderId, Collectors.mapping(
                        i -> new ItemView(i.id(), i.productId(), i.productName(), i.unitPrice(), i.quantity(), i.amount()),
                        Collectors.toList())));
        return rows.stream().map(r -> new OrderView(r.id(), r.status(), r.totalAmount(), r.orderedAt(),
                items.getOrDefault(r.id(), List.of()))).toList();
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }
}
