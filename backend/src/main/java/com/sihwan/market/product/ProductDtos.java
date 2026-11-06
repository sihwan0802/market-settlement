package com.sihwan.market.product;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ProductDtos {
    private ProductDtos() {}

    public record CreateRequest(
            @NotBlank @Size(max = 200) String name,
            @Min(0) long price,
            @Min(0) int stock,
            boolean flashSale) {}

    public record UpdateRequest(
            @NotBlank @Size(max = 200) String name,
            @Min(0) long price,
            @Min(0) int stock,
            @Pattern(regexp = "ON_SALE|SOLD_OUT|HIDDEN") String status) {}

    public record CreatedResponse(long id) {}
}
