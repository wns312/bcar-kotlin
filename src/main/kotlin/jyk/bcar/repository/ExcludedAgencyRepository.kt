package jyk.bcar.repository

import jyk.bcar.domain.ExcludedAgencies

interface ExcludedAgencyRepository {
    suspend fun findExcludedAgencies(): ExcludedAgencies
}
