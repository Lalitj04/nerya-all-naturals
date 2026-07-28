package com.nerya.neryaallnaturals.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.nerya.neryaallnaturals.entity.MediaAsset;
import com.nerya.neryaallnaturals.repository.MediaAssetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Hero/banner images can carry display copy (title/subtitle) and a click-through link (V12). */
class MediaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MediaAssetRepository mediaAssetRepository;

    @Test
    void heroAsset_exposesTitleSubtitleAndLink_onPublicEndpoint() {
        MediaAsset hero = MediaAsset.builder()
                .storageKey("nerya/hero/summer-" + UUID.randomUUID())
                .fileName("hero-summer-sale.jpg")
                .category(MediaAsset.MediaCategory.HERO)
                .publicUrl("https://res.cloudinary.com/demo/image/upload/hero.jpg")
                .altText("Summer sale hero")
                .title("Summer Sale")
                .subtitle("Up to 40% off all naturals")
                .linkUrl("/products?sale=summer")
                .linkText("Shop now")
                .sortOrder(0)
                .isActive(true)
                .build();
        mediaAssetRepository.save(hero);

        // Public, unauthenticated fetch by category.
        ResponseEntity<JsonNode> response = rest.getForEntity("/api/media/category/HERO", JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode match = null;
        for (JsonNode item : response.getBody()) {
            if ("Summer Sale".equals(item.path("title").asText())) {
                match = item;
                break;
            }
        }
        assertThat(match).as("uploaded hero asset should be returned").isNotNull();
        assertThat(match.get("subtitle").asText()).isEqualTo("Up to 40% off all naturals");
        assertThat(match.get("linkUrl").asText()).isEqualTo("/products?sale=summer");
        assertThat(match.get("linkText").asText()).isEqualTo("Shop now");
        assertThat(match.get("publicUrl").asText()).isEqualTo("https://res.cloudinary.com/demo/image/upload/hero.jpg");
    }
}
