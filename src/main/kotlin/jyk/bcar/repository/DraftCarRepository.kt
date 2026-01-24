package jyk.bcar.repository

import jyk.bcar.domain.DraftCar

interface DraftCarRepository {
    suspend fun updateAll(drafts: List<DraftCar>)

    suspend fun findAll(): List<DraftCar>
}
