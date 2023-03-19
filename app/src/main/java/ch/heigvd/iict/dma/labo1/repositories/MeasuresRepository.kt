package ch.heigvd.iict.dma.labo1.repositories

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import ch.heigvd.iict.dma.labo1.models.*
import ch.heigvd.iict.dma.labo1.protobuf.MeasuresOuterClass
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.protobuf.Timestamp
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

            if (compression == Compression.DEFLATE)
                conn.setRequestProperty("X-Content-Encoding", "DEFLATE")



            val elapsed = measureTimeMillis {
                Log.e("SendViewModel", "Send measures to $url")

                when (serialisation) {
                    Serialisation.XML -> {
                        conn.setRequestProperty("Content-Type", "application/xml")
                        val xml = toXML(measures.value!!)
                        Log.d("SendViewModel", "XML: $xml")
                        conn.outputStream.use { output ->
                            output.write(xml.toByteArray(Charsets.UTF_8))
                        }
                    }
                    Serialisation.JSON -> {
                        conn.setRequestProperty("Content-Type", "application/json")
                        val json = toJson(measures.value!!)

                        Log.d("SendViewModel", "JSON: $json")

                        conn.outputStream.use { output ->
                            output.write(json.toByteArray(Charsets.UTF_8))
                        }
                    }
                    Serialisation.PROTOBUF -> {
                        val data = toProtoBuf(measures.value!!)
                        Log.d("Protobuf", data.toString())
                        conn.setRequestProperty("Content-Type", "application/protobuf")
                        conn.outputStream.use { output ->
                            output.write(data)
                        }
                    }
                }

                if (conn.responseCode != 200) {
                    throw Exception("Error while sending measures to server, Error:" + conn.responseCode + " | " + conn.headerFields.get("X-Error"))
                }


                when (serialisation) {
                    Serialisation.XML -> {
                        var dataString = "";
                        BufferedReader(InputStreamReader(conn.inputStream)).use { br ->
                            dataString = br.readText()
                            Log.d("Response", dataString)
                        }

                        val responseList = fromXML(InputSource(StringReader(dataString)))
                        measures.value?.forEach { measure ->
                            responseList[measure.id]?.let { response ->
                                measure.status = response.status
                            }
                        }
                    }
                    Serialisation.JSON -> {
                        var dataString = "";
                        BufferedReader(InputStreamReader(conn.inputStream)).use { br ->
                            dataString = br.readText()
                            Log.d("Response", dataString)
                        }

                        val responseList = fromJson(dataString)
                        measures.value?.forEach { measure ->
                            responseList[measure.id]?.let { response ->
                                measure.status = response.status
                            }
                        }
                    }
                    Serialisation.PROTOBUF -> {
                        val baos = ByteArrayOutputStream()
                        val byteChunk = ByteArray(4096)
                        var n: Int
                        while (conn.inputStream.read(byteChunk).also { n = it } > 0) {
                            baos.write(byteChunk, 0, n)
                        }
                        val responseList = fromProtoBuf(baos.toByteArray())
                        measures.value?.forEach { measure ->
                            responseList[measure.id]?.let { response ->
                                measure.status = response.status
                            }
                        }
                    }
                }
            }
            _requestDuration.postValue(elapsed)
        }
    }

    private fun toProtoBuf(measures: List<Measure>) : ByteArray {
        val measuresProto = measures.map {
            val type = when(it.type){
                Measure.Type.TEMPERATURE -> MeasuresOuterClass.Measure.Type.TEMPERATURE
                Measure.Type.PRECIPITATION -> MeasuresOuterClass.Measure.Type.PRECIPITATION
                Measure.Type.HUMIDITY -> MeasuresOuterClass.Measure.Type.HUMIDITY
                Measure.Type.PRESSURE -> MeasuresOuterClass.Measure.Type.PRESSURE
            }

            val status = when(it.status){
                Measure.Status.NEW -> MeasuresOuterClass.Measure.Status.NEW
                Measure.Status.OK -> MeasuresOuterClass.Measure.Status.OK
                Measure.Status.ERROR -> MeasuresOuterClass.Measure.Status.ERROR
            }

            val timeInMillis = it.date.timeInMillis
            val seconds = timeInMillis / 1000
            val nanos = (timeInMillis % 1000) * 1000000
            val timestamp = Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos.toInt()).build()

            MeasuresOuterClass.Measure.newBuilder()
                .setId(it.id)
                .setDate(timestamp)
                .setType(type)
                .setStatus(status)
                .setValue(it.value)
                .build()
        }

        return MeasuresOuterClass.Measures.newBuilder()
            .addAllMeasures(measuresProto)
            .build().toByteArray()
    }

    private fun fromProtoBuf(measures: ByteArray) : List<Measure> {
        val measures = MeasuresOuterClass.Measures.parseFrom(measures).measuresList.map {
            val type = when(it.type){
                MeasuresOuterClass.Measure.Type.TEMPERATURE -> Measure.Type.TEMPERATURE
                MeasuresOuterClass.Measure.Type.PRECIPITATION -> Measure.Type.PRECIPITATION
                MeasuresOuterClass.Measure.Type.HUMIDITY -> Measure.Type.HUMIDITY
                MeasuresOuterClass.Measure.Type.PRESSURE -> Measure.Type.PRESSURE
                else -> throw java.lang.RuntimeException()
            }

            val status = when(it.status){
                MeasuresOuterClass.Measure.Status.NEW -> Measure.Status.NEW
                MeasuresOuterClass.Measure.Status.OK -> Measure.Status.OK
                MeasuresOuterClass.Measure.Status.ERROR -> Measure.Status.ERROR
                else -> throw java.lang.RuntimeException()
            }

            val seconds = it.date.seconds
            val nanos = it.date.nanos
            val timeInMillis = seconds * 1000 + nanos / 1000000
            val calendar = Calendar.getInstance()

            Measure(it.id, status, type, it.value, calendar)
        }

        return measures
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