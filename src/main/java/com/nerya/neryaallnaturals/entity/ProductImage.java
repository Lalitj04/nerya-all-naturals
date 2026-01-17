package com.nerya.neryaallnaturals.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Entity
@Table(name = "product_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @NotBlank(message = "Image URL is required")
    @Size(max = 500)
    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(name = "alt_text")
    @Size(max = 200)
    private String altText;

    @Column(name = "is_primary")
    @Builder.Default
    private Boolean isPrimary = false;

    @Column(name = "display_order")
    private Integer displayOrder = 0; // For ordering images (0 = first)
}

