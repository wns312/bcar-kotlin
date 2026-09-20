package jyk.bcar.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "batch")
data class BatchProperties(
    val jobs: Map<String, Target> = emptyMap(),
    val detailShards: Int = 8,
    // 체인 폭주 방지 fuse. 정상 종료는 남은 차량 0건 또는 _control.stopDetail
    val detailMaxHops: Int = 300,
) {
    data class Target(
        val queue: String,
        val definition: String,
    )
}
