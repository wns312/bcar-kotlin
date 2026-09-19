package jyk.bcar.repository

import jyk.bcar.domain.Car

interface CarRepository {
    /** DynamoDB 병렬 Scan 세그먼트. 여러 배치 잡이 테이블을 서로소로 나눠 읽을 때 사용 */
    suspend fun findAll(segment: Int = 0, totalSegments: Int = 1): List<Car>

    suspend fun saveAll(cars: List<Car>)
}
