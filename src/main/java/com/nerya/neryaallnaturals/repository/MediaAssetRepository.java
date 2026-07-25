package com.nerya.neryaallnaturals.repository;

import com.nerya.neryaallnaturals.entity.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    List<MediaAsset> findByCategoryAndIsActiveTrueOrderBySortOrderAsc(MediaAsset.MediaCategory category);

    List<MediaAsset> findByProductIdAndIsActiveTrue(Long productId);

    Optional<MediaAsset> findByDriveFileId(String driveFileId);

    List<MediaAsset> findByIsActiveTrueOrderBySortOrderAsc();

    List<MediaAsset> findByCategoryAndProductIdAndIsActiveTrueOrderBySortOrderAsc(
            MediaAsset.MediaCategory category, Long productId);

    List<MediaAsset> findByProductIdInAndIsActiveTrue(List<Long> productIds);

    // Category-tile images: linked to a Category entity via category_id.
    List<MediaAsset> findByLinkedCategoryIdAndIsActiveTrueOrderBySortOrderAsc(Long categoryId);

    List<MediaAsset> findByLinkedCategoryIdInAndIsActiveTrue(List<Long> categoryIds);

    // Blog post images: linked to a Blog entity via blog_id.
    List<MediaAsset> findByLinkedBlogIdAndIsActiveTrueOrderBySortOrderAsc(Long blogId);

    List<MediaAsset> findByLinkedBlogIdInAndIsActiveTrue(List<Long> blogIds);
}
