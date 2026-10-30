package jyk.bcar.client

import com.google.api.services.sheets.v4.Sheets
import org.springframework.stereotype.Component

@Component
class GoogleSheetsClient(
    private val sheets: Sheets,
) {
    fun readRange(
        spreadsheetId: String,
        sheet: String,
        rangeA1: String = "A:Z",
    ): List<List<Any>> {
        val res =
            sheets
                .spreadsheets()
                .values()
                .get(spreadsheetId, "$sheet!$rangeA1") // 예: "Sheet1!A1:D10"
                .execute()

        return res.getValues() ?: emptyList()
    }
}
