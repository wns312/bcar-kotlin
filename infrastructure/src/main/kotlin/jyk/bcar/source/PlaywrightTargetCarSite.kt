package jyk.bcar.source

import jyk.bcar.automation.job.act.target.SyncUploadedCars
import jyk.bcar.automation.job.act.target.SyncUploadedCarsRequest
import jyk.bcar.automation.job.act.target.TargetAdminLogin
import jyk.bcar.automation.job.act.target.UploadCar
import jyk.bcar.automation.job.act.target.UploadCarRequest
import jyk.bcar.automation.playwright.PlaywrightSessionRunner
import jyk.bcar.domain.TargetAdminUser
import jyk.bcar.domain.UploadSource
import jyk.bcar.port.SiteListings
import jyk.bcar.port.TargetCarSession
import jyk.bcar.port.TargetCarSite
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class PlaywrightTargetCarSite(
    private val runner: PlaywrightSessionRunner,
    private val webClient: WebClient,
) : TargetCarSite {
    override suspend fun <T> withSession(user: TargetAdminUser, block: suspend (TargetCarSession) -> T): T =
        runner.withSession { session ->
            session.usePage { page ->
                TargetAdminLogin(page).doAct(user)
                block(object : TargetCarSession {
                    override suspend fun synchronize(expected: Set<String>, delete: Boolean): SiteListings {
                        val result = SyncUploadedCars(page).doAct(SyncUploadedCarsRequest(user.manageUrl, expected, delete))
                        return SiteListings(result.found, result.deleted, result.paid)
                    }

                    override suspend fun upload(source: UploadSource, price: Int, comment: String, submit: Boolean) {
                        UploadCar(page, webClient).doAct(UploadCarRequest(source, user.registerUrl, price, comment, submit))
                    }
                })
            }
        }
}
