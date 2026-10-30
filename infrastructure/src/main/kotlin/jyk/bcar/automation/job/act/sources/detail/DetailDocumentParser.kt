package jyk.bcar.automation.job.act.sources.detail

import jyk.bcar.automation.job.act.JobAct
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.ByteArrayInputStream

class DetailDocumentParser : JobAct<DetailExtractorRequest, Document> {
    /**
     * baseUri: 문서에 상대경로로 들어간 링크를 baseUri를 붙여줌
     * */
    override suspend fun doAct(input: DetailExtractorRequest): Document {
        return Jsoup.parse(
            ByteArrayInputStream(input.htmlBytes),
            input.charSet.charsetName,
            input.baseUri,
        )
    }
}
