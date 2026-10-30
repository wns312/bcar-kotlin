package jyk.bcar.configuration

import jyk.bcar.domain.CarCategory
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "assign")
data class AssignProperties(
    /** 유저 quota를 카테고리별로 나누는 이상 비율. 합 1.0 */
    val ratio: Map<CarCategory, Double>,
    /** 카테고리 부족분을 대신 채울 카테고리 순서 */
    val fallbackOrder: List<CarCategory>,
)
