package com.sihwan.market.product;

import com.sihwan.market.product.ProductDtos.CreateRequest;
import com.sihwan.market.product.ProductDtos.UpdateRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductService {

    private final ProductMapper products;

    public ProductService(ProductMapper products) {
        this.products = products;
    }

    @Transactional
    public long create(long memberId, CreateRequest req) {
        long sellerId = sellerId(memberId);
        long id = products.insertProduct(sellerId, req.name(), req.price(), req.flashSale());
        products.insertInventory(id, req.stock());
        return id;
    }

    public Product get(long id) {
        return products.findById(id)
                .filter(p -> !"HIDDEN".equals(p.status()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "상품이 없습니다"));
    }

    public List<Product> list(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        return products.findVisible(safeSize, Math.max(page, 0) * safeSize);
    }

    @Transactional
    public void update(long memberId, long id, UpdateRequest req) {
        owned(memberId, id);
        products.updateProduct(id, req.name(), req.price(), req.status());
        products.updateStock(id, req.stock());
    }

    // 주문 항목이 상품을 참조하므로 행을 지우지 않고 숨긴다
    @Transactional
    public void delete(long memberId, long id) {
        owned(memberId, id);
        products.hide(id);
    }

    private long sellerId(long memberId) {
        return products.findSellerId(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "판매자 정보가 없습니다"));
    }

    private Product owned(long memberId, long id) {
        Product p = products.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "상품이 없습니다"));
        if (p.sellerId() != sellerId(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "다른 판매자의 상품입니다");
        }
        return p;
    }
}
