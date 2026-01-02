package jyk.bcar.client

import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ClearValuesRequest
import com.google.api.services.sheets.v4.model.ValueRange
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

    fun clearRange(
        spreadsheetId: String,
        sheet: String,
        rangeA1: String = "A:Z",
    ) {
        val request = ClearValuesRequest()
        sheets
            .spreadsheets()
            .values()
            .clear(spreadsheetId, "$sheet!$rangeA1", request)
            .execute()
    }

    fun updateRange(
        spreadsheetId: String,
        sheet: String,
        rangeA1: String,
        values: List<List<Any>>,
    ) {
        val request = ValueRange().setValues(values)
        sheets
            .spreadsheets()
            .values()
            .update(spreadsheetId, "$sheet!$rangeA1", request)
            .setValueInputOption("RAW")
            .execute()
    }
}
