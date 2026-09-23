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
        private const val SITE_SHEET_NAME = "사이트정보"
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

        val baseUrls = findBaseUrls()
        // 주소를 모르는 유저를 건너뛰면 그 계정만 조용히 동기화되지 않는다. 시트가 틀렸으면 즉시 실패해야 한다
        val unknownSites = result.mapNotNull { it.getOrNull(2) as? String }.distinct().filterNot { it in baseUrls }
        check(unknownSites.isEmpty()) { "No baseUrl in '$SITE_SHEET_NAME' for targetSite: $unknownSites" }

        return result.mapIndexedNotNull { i, row ->
            try {
                check(row.size == 4)

                val (id, password, targetSite, quota) = row

                check(id is String && password is String && targetSite is String && quota is String)

                TargetAdminUser(
                    id = id,
                    password = password,
                    targetSite = targetSite,
                    quota = quota.trim().toInt(),
                    baseUrl = baseUrls.getValue(targetSite),
                )
            } catch (e: Exception) {
                // 행에 비밀번호가 있으니 내용은 찍지 않는다
                logger.error("Target admin user row ${i + 2} skipped: ${e.message} (columns=${row.size})")
                null
            }
        }
    }

    private fun findBaseUrls(): Map<String, String> =
        googleSheetsClient
            .readRange(
                spreadsheetId = googleProperties.sheets.id,
                sheet = SITE_SHEET_NAME,
                rangeA1 = "A2:B", // label을 제외한 두번째 row부터 targetSite, baseUrl
            ).filter { it.size == 2 }
            .associate { (targetSite, baseUrl) -> targetSite as String to baseUrl as String }
}
