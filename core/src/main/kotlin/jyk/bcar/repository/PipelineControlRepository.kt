package jyk.bcar.repository

interface PipelineControlRepository {
    /** `_control` 아이템의 stopDetail 플래그. 상세 수집 체인을 밖에서 세우는 스위치 */
    suspend fun isDetailCollectionStopped(): Boolean

    /** 끝난 detail 체인 수를 원자적으로 1 올리고 그 값을 돌려준다. shards와 같아지면 마지막 체인이다 */
    suspend fun markDetailChainDone(): Int

    suspend fun resetDetailChains()
}
