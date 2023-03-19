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
import org.xml.sax.InputSource
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.zip.DeflaterOutputStream
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

            if (networkType != NetworkType.RANDOM)
                conn.setRequestProperty("X-Network", networkType.toString())

            val elapsed = measureTimeMillis {

                var serialized : String = ""
                when (serialisation) {
                    Serialisation.XML -> {
                        conn.setRequestProperty("Content-Type", "application/xml")
                        serialized = toXML(measures.value!!)
                    }
                    Serialisation.JSON -> {
                        conn.setRequestProperty("Content-Type", "application/json")
                        serialized = toJson(measures.value!!)
                    }
                    Serialisation.PROTOBUF -> {
                        //TODO
                    }
                }

                Log.d("SendContent", "$serialized")
                var toSend : ByteArray = serialized.toByteArray(Charsets.UTF_8)

                // Compression if needed
                if (compression == Compression.DEFLATE) {
                    conn.setRequestProperty("X-Content-Encoding", "DEFLATE")
                    var arrayOutputStream = ByteArrayOutputStream()
                    var outputStream = DeflaterOutputStream(arrayOutputStream)
                    outputStream.write(toSend)
                    outputStream.flush()
                    outputStream.close()
                    toSend = arrayOutputStream.toByteArray()
                }

                conn.outputStream.use { output ->
                    output.write(toSend)
                }

                if (conn.responseCode != 200) {
                    throw Exception("Error while sending measures to server")
                }
                var data = "";
                BufferedReader(InputStreamReader(conn.inputStream)).use { br ->
                    data = br.readText()
                    Log.d("Response", data)
                }

                if (serialisation == Serialisation.XML) {
                    val responseList = fromXML(InputSource(StringReader(data)))
                    measures.value?.forEach { measure ->
                        responseList[measure.id]?.let { response ->
                            measure.status = response.status
                        }
                    }
                } else {
                    val responseList = fromJson(data)
                    measures.value?.forEach { measure ->
                        responseList[measure.id]?.let { response ->
                            measure.status = response.status
                        }
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
            document.rootElement.addContent(measures.map { measure ->
                val measureElement = org.jdom2.Element("measure")
                measureElement.setAttribute("id", measure.id.toString())
                measureElement.setAttribute("status", measure.status.toString())
                measureElement.addContent(org.jdom2.Element("type").setText(measure.type.toString()))
                measureElement.addContent(org.jdom2.Element("value").setText(measure.value.toString()))
                measureElement.addContent(org.jdom2.Element("date").setText(measure.date.toString()))
                measureElement
            })
            return org.jdom2.output.XMLOutputter().outputString(document)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return ""
    }
    private fun fromXML(xml: InputSource) : Map<Int, ResponseMessage> {
        val messages = mutableMapOf<Int, ResponseMessage>()
        try {
            val builder = org.jdom2.input.SAXBuilder()
            builder.setFeature("http://xml.org/sax/features/external-general-entities", false)
            val document = builder.build(xml)
            document.rootElement.children.forEach { responseElement ->
                val id = responseElement.getAttributeValue("id").toInt()
                val status = when (responseElement.getAttributeValue("status")) {
                    "OK" -> Measure.Status.OK
                    "ERROR" -> Measure.Status.ERROR
                    "NEW" -> Measure.Status.NEW
                    else -> throw Exception("Unknown status")
                }
                messages[id] = ResponseMessage(id, status)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return messages
    }
}