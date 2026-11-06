package com.sihwan.market.order;

import com.sihwan.market.order.OrderDtos.CreateRequest;
import com.sihwan.market.order.OrderDtos.CreatedResponse;
import com.sihwan.market.order.OrderDtos.Item;
import com.sihwan.market.product.Product;
import com.sihwan.market.product.ProductMapper;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {

    private final OrderMapper orders;
    private final ProductMapper products;

    public OrderService(OrderMapper orders, ProductMapper products) {
        this.orders = orders;
        this.products = products;
    }

    @Transactional
    public CreatedResponse create(long buyerId, CreateRequest req) {
        // 상품 id 순으로 잠그면 서로 다른 순서로 주문해도 데드락이 나지 않는다
        List<Item> items = req.items().stream().sorted(Comparator.comparingLong(Item::productId)).toList();
        for (int i = 1; i < items.size(); i++) {
            if (items.get(i).productId() == items.get(i - 1).productId()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "같은 상품이 중복되었습니다");
            }
        }

        long total = 0;
        for (Item item : items) {
            Product p = products.findById(item.productId())
                    .filter(x -> "ON_SALE".equals(x.status()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "구매할 수 없는 상품입니다"));
            if (orders.decreaseStock(p.id(), item.quantity()) == 0) {
                // 예외로 트랜잭션 전체가 롤백되어 앞에서 차감한 재고도 복구된다
                throw new ResponseStatusException(HttpStatus.CONFLICT, "재고가 부족합니다");
            }
            total += p.price() * item.quantity();
        }

        long orderId = orders.insertOrder(buyerId, total);
        for (Item item : items) {
            Product p = products.findById(item.productId()).orElseThrow();
            orders.insertItem(orderId, p.id(), p.sellerId(), p.price(), item.quantity(), p.price() * item.quantity());
        }
        return new CreatedResponse(orderId, total);
    }
}
