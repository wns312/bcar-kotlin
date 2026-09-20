package jyk.bcar.repository

import jyk.bcar.domain.Car

interface CarRepository {
    /** DynamoDB 병렬 Scan 세그먼트. 여러 배치 잡이 테이블을 서로소로 나눠 읽을 때 사용 */
    suspend fun findAll(segment: Int = 0, totalSegments: Int = 1): List<Car>

    /** assignedUserId GSI Query. 비활성 차량도 포함된다 */
    suspend fun findByAssignedUser(userId: String): List<Car>

    suspend fun saveAll(cars: List<Car>)

    /** `_control` 아이템의 stopDetail 플래그. 상세 수집 체인을 밖에서 세우는 스위치 */
    suspend fun isDetailCollectionStopped(): Boolean
}
