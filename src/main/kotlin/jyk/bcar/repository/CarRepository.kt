package jyk.bcar.repository

import jyk.bcar.domain.Car

interface CarRepository {
    suspend fun findAll(): List<Car>

    suspend fun saveAll(cars: List<Car>)
}
