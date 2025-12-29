package jyk.bcar.repository

import jyk.bcar.domain.SourceAdminUser
import jyk.bcar.domain.TargetAdminUser

interface UserRepository {
    suspend fun findSourceAdminUser(): SourceAdminUser

    suspend fun findAllTargetAdminUsers(): List<TargetAdminUser>
}
