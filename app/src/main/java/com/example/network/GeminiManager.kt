package com.example.network

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GeminiManager {
    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    suspend fun extractRosterFromImages(bitmaps: List<Bitmap>): ExtractedRoster? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw Exception("Gemini API Key is not set! Please configure your GEMINI_API_KEY in the Secrets panel in AI Studio.")
        }

        val promptText = """
            You are given one or more images of a 'WORKERS SHIFT ALLOCATION' spreadsheet.
            Your task is to extract all workers, their assigned sections, and their shift (Day Shift or Night Shift) from the document.
            
            Look closely at each row and column:
            - Sections are listed in headers, like 'GRINDING SEC', 'WIRE DRAWING', 'MAINTENANCE', 'SHAFT GRINDING', 'Q/C LINE INSPECTION', 'QUALITY (SHAFT SECTION)', 'FINAL INSPECTION', 'LAB/ INCOMING', 'ELECTRICAL', 'HOUSE KEEPING', 'SECURITY GUARD'.
            - Identify each worker's section from the preceding header under which they are grouped.
            - If a worker's name is listed under the 'DAY SHIFT' column, their shift is 'Day'.
            - If a worker's name is listed under the 'NIGHT SHIFT' column, their shift is 'Night'.
            - Extract their names in clean English, fully UPPERCASE (e.g. 'RAMESH', 'PANKAJ', 'VIJAYAN'). Do not include any trailing check marks, commas, or random marks in the name.
            - Extract the section name in clean English, fully UPPERCASE (e.g. 'GRINDING SEC', 'WIRE DRAWING').
            - Also extract the week label from the sheet (usually at the top of the page, e.g., 'WORKERS SHIFT ALLOCATION FROM 22.06.2026 TO 27.06.2026' or similar).
            
            Return the result strictly as a JSON object matching this schema:
            {
              "weekLabel": "22.06.2026 to 27.06.2026",
              "workers": [
                {
                  "name": "RAMESH",
                  "section": "GRINDING SEC",
                  "shift": "Day"
                },
                ...
              ]
            }
            Do not include any markdown format tags or explanations. Return ONLY valid JSON conforming to the requested schema.
        """.trimIndent()

        val parts = mutableListOf<Part>()
        parts.add(Part(text = promptText))
        bitmaps.forEach { bmp ->
            parts.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = bmp.toBase64())))
        }

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = parts)),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.1
            )
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: return@withContext null
            
            return@withContext RetrofitClient.rosterAdapter.fromJson(jsonText)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
