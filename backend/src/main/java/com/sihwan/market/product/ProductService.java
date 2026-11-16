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
    private final ProductCache cache;

    public ProductService(ProductMapper products, ProductCache cache) {
        this.products = products;
        this.cache = cache;
    }

    @Transactional
    public long create(long memberId, CreateRequest req) {
        long sellerId = sellerId(memberId);
        long id = products.insertProduct(sellerId, req.name(), req.price(), req.flashSale());
        products.insertInventory(id, req.stock());
        cache.evictAfterCommit(id);
        return id;
    }

    public Product get(long id) {
        return cache.getDetail(id).orElseGet(() -> {
            Product p = products.findById(id)
                    .filter(found -> !"HIDDEN".equals(found.status()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "상품이 없습니다"));
            cache.putDetail(p);
            return p;
        });
    }

    public List<Product> list(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return cache.getList(safePage, safeSize).orElseGet(() -> {
            List<Product> list = products.findVisible(safeSize, safePage * safeSize);
            cache.putList(safePage, safeSize, list);
            return list;
        });
    }

    @Transactional
    public void update(long memberId, long id, UpdateRequest req) {
        owned(memberId, id);
        products.updateProduct(id, req.name(), req.price(), req.status());
        products.updateStock(id, req.stock());
        cache.evictAfterCommit(id);
    }

    // 주문 항목이 상품을 참조하므로 행을 지우지 않고 숨긴다
    @Transactional
    public void delete(long memberId, long id) {
        owned(memberId, id);
        products.hide(id);
        cache.evictAfterCommit(id);
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
