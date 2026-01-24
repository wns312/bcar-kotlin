package jyk.bcar.repository

import jyk.bcar.client.GoogleSheetsClient
import jyk.bcar.configuration.GoogleProperties
import jyk.bcar.domain.DraftCar
import org.springframework.stereotype.Component

@Component
class GoogleSheetsDraftCarRepository(
    private val googleSheetsClient: GoogleSheetsClient,
    private val googleProperties: GoogleProperties,
) : DraftCarRepository {
    companion object {
        private const val DRAFT_SHEET_NAME = "Draft목록"
    }

    override suspend fun updateAll(drafts: List<DraftCar>) {
        val header = listOf("차량 번호", "차량 제목", "차량 제조사", "상세페이지 번호", "중고차 사무실", "판매자", "판매자 휴대전화", "가격")

        val rows = drafts.map {
            listOf(it.carNumber, it.title, it.company, it.detailPageNum, it.agency, it.seller, it.sellerPhone, it.price)
        }

        val values: List<List<Any>> = buildList {
            add(header)
            addAll(rows)
        }

        googleSheetsClient.clearRange(
            spreadsheetId = googleProperties.sheets.id,
            sheet = DRAFT_SHEET_NAME,
        )

        googleSheetsClient.updateRange(
            spreadsheetId = googleProperties.sheets.id,
            sheet = DRAFT_SHEET_NAME,
            rangeA1 = "A1",
            values = values,
        )
    }

    override suspend fun findAll(): List<DraftCar> {
        val result = googleSheetsClient.readRange(
            spreadsheetId = googleProperties.sheets.id,
            sheet = DRAFT_SHEET_NAME,
            rangeA1 = "A2:Z",
        )

        return result.map {
            check(it.size == 7)

            val carNumber = it[0]
            val title = it[1]
            val company = it[2]
            val detailPageNum = it[3]
            val agency = it[4]
            val seller = it[5]
            val sellerPhone = it[6]
            val price = it[7]

            check(
                carNumber is String &&
                    title is String &&
                    company is String &&
                    detailPageNum is String &&
                    agency is String &&
                    seller is String &&
                    sellerPhone is String &&
                    price is String,
            )

            DraftCar(
                carNumber = carNumber,
                title = title,
                company = company,
                detailPageNum = detailPageNum,
                agency = agency,
                seller = seller,
                sellerPhone = sellerPhone,
                price = price.toInt(),
            )
        }
    }
}
