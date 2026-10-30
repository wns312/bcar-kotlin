package jyk.bcar.policy

import jyk.bcar.domain.CarCategory
import kotlin.math.abs

data class AssignPolicy(
    /** 유저 quota를 카테고리별로 나누는 이상 비율. 합 1.0 */
    val ratio: Map<CarCategory, Double>,
    /** 카테고리 부족분을 대신 채울 카테고리 순서 */
    val fallbackOrder: List<CarCategory>,
) {
    init {
        require(abs(ratio.values.sum() - 1.0) < 1e-6) { "assign.ratio must sum to 1.0: $ratio" }
    }
}
