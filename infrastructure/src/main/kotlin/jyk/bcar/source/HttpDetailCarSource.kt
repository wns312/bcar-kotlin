package jyk.bcar.source

import jyk.bcar.automation.job.act.sources.CharSet
import jyk.bcar.automation.job.act.sources.detail.CollectDetailPageBytes
import jyk.bcar.automation.job.act.sources.detail.CollectDetailPageBytesRequest
import jyk.bcar.automation.job.act.sources.detail.DetailExtractor
import jyk.bcar.automation.job.act.sources.detail.DetailExtractorRequest
import jyk.bcar.port.DetailCarSource
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class HttpDetailCarSource(
    webClient: WebClient,
) : DetailCarSource {
    private val fetcher = CollectDetailPageBytes(webClient)
    private val extractor = DetailExtractor()

    override suspend fun fetch(detailPageNumber: String): ByteArray =
        fetcher.doAct(CollectDetailPageBytesRequest(detailPageNumber))

    override suspend fun parse(bytes: ByteArray) =
        extractor.doAct(DetailExtractorRequest(bytes, CharSet.EUC_KR, baseUri = ""))
}
