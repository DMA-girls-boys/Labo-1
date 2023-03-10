package ch.heigvd.iict.dma.labo1.models

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.text.SimpleDateFormat
import java.util.*

class CalendarTypeAdapter : TypeAdapter<Calendar?>() {
    override fun write(out: JsonWriter?, value: Calendar?) {
        out?.value(toString(value!!))
    }

    override fun read(i: JsonReader?): Calendar {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        val date = dateFormat.parse(i?.nextString())
        val calendar = Calendar.getInstance()
        calendar.time = date
        return calendar
    }

    companion object {
        fun toString(calendar: Calendar): String {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
            return dateFormat.format(calendar.time)
        }
    }
}