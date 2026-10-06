package jyk.bcar.repository

import jyk.bcar.domain.CategoryTree

interface CategoryTreeRepository {
    /** `_categories` 아이템에 든 대상 사이트 분류 트리. 아직 수집 전이면 null */
    suspend fun findCategoryTree(): CategoryTree?

    suspend fun saveCategoryTree(tree: CategoryTree)
}
