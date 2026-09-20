package jyk.bcar.repository

import jyk.bcar.client.GoogleSheetsClient
import jyk.bcar.configuration.GoogleProperties
import jyk.bcar.domain.SourceAdminUser
import jyk.bcar.domain.TargetAdminUser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class GoogleSheetsUserRepository(
    private val googleSheetsClient: GoogleSheetsClient,
    private val googleProperties: GoogleProperties,
) : UserRepository {
    companion object {
        private const val SOURCE_ADMIN_USER_SHEET_NAME = "관리자계정정보"
        private const val TARGET_ADMIN_USER_SHEET_NAME = "교차로계정정보"
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun findSourceAdminUser(): SourceAdminUser {
        val result =
            googleSheetsClient.readRange(
                googleProperties.sheets.id,
                SOURCE_ADMIN_USER_SHEET_NAME,
            )
        check(result.size == 2 && result[0].size == 2 && result[1].size == 2)

        val id = result[0][1]
        val password = result[1][1]

        check(id is String && password is String)

        return SourceAdminUser(id = id, password = password)
    }

    override suspend fun findAllTargetAdminUsers(): List<TargetAdminUser> {
        val result =
            googleSheetsClient.readRange(
                spreadsheetId = googleProperties.sheets.id,
                sheet = TARGET_ADMIN_USER_SHEET_NAME,
                rangeA1 = "A2:D", // label을 제외한 두번째 row부터 id, password, targetSite, quota
            )

        return result.mapIndexedNotNull { i, row ->
            try {
                check(row.size == 4)

                val (id, password, targetSite, quota) = row

                check(id is String && password is String && targetSite is String && quota is String)

                TargetAdminUser(id = id, password = password, targetSite = targetSite, quota = quota.trim().toInt())
            } catch (_: Exception) {
                // 행에 비밀번호가 있으니 내용은 찍지 않는다
                logger.error(
                    "Target admin user row ${i + 2} skipped: expected 4 columns (id, password, targetSite, quota), got ${row.size}",
                )
                null
            }
        }
    }
}
