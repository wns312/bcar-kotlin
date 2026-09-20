package jyk.bcar.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "batch")
data class BatchProperties(
    val jobs: Map<String, Target> = emptyMap(),
    val detailShards: Int = 8,
    // 체인 폭주 방지 fuse. 정상 종료는 남은 차량 0건 또는 _control.stopDetail
    val detailMaxHops: Int = 300,
    // 연속 0건 hop이 이만큼이면 사이트가 죽었거나 전면 차단 — fuse까지 기다리지 않고 종료
    val detailMaxIdleHops: Int = 5,
) {
    data class Target(
        val queue: String,
        val definition: String,
    )
}
