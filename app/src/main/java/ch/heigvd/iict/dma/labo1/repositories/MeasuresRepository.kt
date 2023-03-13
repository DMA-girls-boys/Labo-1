package ch.heigvd.iict.dma.labo1.repositories

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import ch.heigvd.iict.dma.labo1.models.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jdom2.DocType
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import kotlin.system.measureTimeMillis

class MeasuresRepository(private val scope : CoroutineScope,
                         private val dtd : String = "https://mobile.iict.ch/measures.dtd",
                         private val httpUrl : String = "http://mobile.iict.ch/api",
                         private val httpsUrl : String = "https://mobile.iict.ch/api") {

    private val _measures = MutableLiveData(mutableListOf<Measure>())
    val measures = Transformations.map(_measures) { mList -> mList.toList() }

    private val _requestDuration = MutableLiveData(-1L)
    val requestDuration : LiveData<Long> get() = _requestDuration

    fun generateRandomMeasures(nbr: Int = 3) {
        addMeasures(Measure.getRandomMeasures(nbr))
    }

    fun resetRequestDuration() {
        _requestDuration.postValue(-1L)
    }

    fun addMeasure(measure: Measure) {
        addMeasures(listOf(measure))
    }

    fun addMeasures(measures: List<Measure>) {
        val l = _measures.value!!
        l.addAll(measures)
        _measures.postValue(l)
    }

    fun clearAllMeasures() {
        _measures.postValue(mutableListOf())
    }

    fun sendMeasureToServer(encryption : Encryption, compression : Compression, networkType : NetworkType, serialisation : Serialisation) {
        scope.launch(Dispatchers.IO) {

            val urlStr = when (encryption) {
                Encryption.DISABLED -> httpUrl
                Encryption.SSL -> httpsUrl
            }

            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection

            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            if (networkType != NetworkType.RANDOM)
                conn.setRequestProperty("X-Network", networkType.toString())

            if (compression == Compression.DEFLATE)
                conn.setRequestProperty("X-Content-Encoding", "DEFLATE")



            val elapsed = measureTimeMillis {
                Log.e("SendViewModel", "Implement me !!! Send measures to $url") //TODO

                if (serialisation == Serialisation.XML) {
                    val xml = toXML(measures.value!!)
                    Log.d("SendViewModel", "XML: $xml")
                    conn.outputStream.use { output ->
                        output.write(xml?.toByteArray(Charsets.UTF_8))
                    }
                } else {
                    val json = toJson(measures.value!!)

                    Log.d("SendViewModel", "JSON: $json")

                    conn.outputStream.use { output ->
                        output.write(json.toByteArray(Charsets.UTF_8))
                    }
                    Log.d("Response", conn.responseCode.toString()) //TODO gérer les erreurs
                }


                var data = "";
                BufferedReader(InputStreamReader(conn.inputStream)).use { br ->
                    data = br.readText()
                    Log.d("Response", data)
                }

                val responseList = fromJson(data)
                Log.d("Response", responseList.toString())

                // iterate on measures
                measures.value?.forEach { measure ->
                    responseList[measure.id]?.let { response ->
                        measure.status = response.status
                    }
                }
            }
            _requestDuration.postValue(elapsed)
        }
    }


    private fun toJson(measures: List<Measure>) : String {
        val gson = GsonBuilder()
            .registerTypeHierarchyAdapter(Calendar::class.java, CalendarTypeAdapter())
            .create()
        return gson.toJson(measures)
    }

    private fun fromJson(json: String) : Map<Int, ResponseMessage> {
        val gson = Gson()
        return gson.fromJson(json, Array<ResponseMessage>::class.java).associateBy { it.id }
    }

    private fun toXML(measures: List<Measure>) : String {
        val docType = DocType("measures", dtd)
        try {
            val root = org.jdom2.Element("measures")
            val document = org.jdom2.Document(root, docType)
            measures.forEach { measure ->
                val measureElement = org.jdom2.Element("measure")
                measureElement.setAttribute("id", measure.id.toString())
                measureElement.setAttribute("status", measure.status.toString())
                measureElement.setAttribute("type", measure.type.toString())
                measureElement.setAttribute("value", measure.value.toString())
                measureElement.setAttribute("date", CalendarTypeAdapter.toString(measure.date))
            }
            return org.jdom2.output.XMLOutputter().outputString(document)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return ""
    }

}