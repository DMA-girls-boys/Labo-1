package ch.heigvd.iict.dma.labo1.repositories

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import ch.heigvd.iict.dma.labo1.models.*
import ch.heigvd.iict.dma.protobuf.MeasuresOuterClass
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jdom2.DocType
import org.xml.sax.InputSource
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import kotlin.system.measureTimeMillis

class MeasuresRepository(
    private val scope: CoroutineScope,
    private val dtd: String = "https://mobile.iict.ch/measures.dtd",
    private val httpUrl: String = "http://mobile.iict.ch/api",
    private val httpsUrl: String = "https://mobile.iict.ch/api"
) {

    private val _measures = MutableLiveData(mutableListOf<Measure>())
    val measures = Transformations.map(_measures) { mList -> mList.toList() }

    private val _requestDuration = MutableLiveData(-1L)
    val requestDuration: LiveData<Long> get() = _requestDuration

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

    fun sendMeasureToServer(
        encryption: Encryption,
        compression: Compression,
        networkType: NetworkType,
        serialisation: Serialisation
    ) {
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

                var toSend: ByteArray
                when (serialisation) {
                    Serialisation.XML -> {
                        conn.setRequestProperty("Content-Type", "application/xml")
                        toSend = toXML(measures.value!!).toByteArray(Charsets.UTF_8)
                    }
                    Serialisation.JSON -> {
                        conn.setRequestProperty("Content-Type", "application/json")
                        val serialized = toJson(measures.value!!)
                        Log.d("JSON", serialized)
                        toSend = serialized.toByteArray(Charsets.UTF_8)
                    }
                    Serialisation.PROTOBUF -> {
                        conn.setRequestProperty("Content-Type", "application/protobuf")
                        toSend = toProtoBuf(measures.value!!)
                    }
                }

                // Compression if needed
                if (compression == Compression.DEFLATE) {
                    Log.d("Compression", "DEFLATE")
                    conn.setRequestProperty("X-Content-Encoding", "DEFLATE")
                    val arrayOutputStream = ByteArrayOutputStream()
                    val outputStream = DeflaterOutputStream(arrayOutputStream, Deflater(8, true))
                    outputStream.write(toSend)
                    outputStream.flush()
                    outputStream.close()
                    toSend = arrayOutputStream.toByteArray()

                    InflaterInputStream(ByteArrayInputStream(toSend), Inflater(true))
                }



                conn.outputStream.use { output ->
                    output.write(toSend)
                }

                if (conn.responseCode != 200) {
                    throw Exception(
                        "Error while sending measures to server, Error:" + conn.responseCode + " | " + conn.headerFields["X-Error"]
                    )
                }


                var dataString = ""
                var bytes = ByteArray(0)
                if (serialisation == Serialisation.PROTOBUF || compression == Compression.DEFLATE) {
                    bytes = conn.inputStream.readBytes()

                    if (compression == Compression.DEFLATE) {
                        val inflaterInputStream =
                            InflaterInputStream(ByteArrayInputStream(bytes), Inflater(true))

                        if (serialisation != Serialisation.PROTOBUF) {
                            dataString = inflaterInputStream.bufferedReader().use { it.readText() }
                        } else {
                            bytes = inflaterInputStream.readBytes()
                        }
                    }

                } else {
                    BufferedReader(InputStreamReader(conn.inputStream)).use { br ->
                        dataString = br.readText()
                    }
                }




                when (serialisation) {
                    Serialisation.XML -> {
                        val responseList = fromXML(InputSource(StringReader(dataString)))
                        measures.value?.forEach { measure ->
                            responseList[measure.id]?.let { response ->
                                measure.status = response.status
                            }
                        }
                    }
                    Serialisation.JSON -> {
                        val responseList = fromJson(dataString)
                        measures.value?.forEach { measure ->
                            responseList[measure.id]?.let { response ->
                                measure.status = response.status
                            }
                        }
                    }
                    Serialisation.PROTOBUF -> {

                        val responseList = MeasuresOuterClass.MeasuresAck.parseFrom(bytes)
                        measures.value?.forEach { measure ->
                            val id = measure.id
                            responseList.getMeasures(id)?.let { response ->
                                val status = when (response.status) {
                                    MeasuresOuterClass.Status.NEW -> Measure.Status.NEW
                                    MeasuresOuterClass.Status.OK -> Measure.Status.OK
                                    MeasuresOuterClass.Status.ERROR -> Measure.Status.ERROR
                                    else -> throw java.lang.RuntimeException()
                                }
                                measure.status = status
                            }
                        }
                    }
                }
            }
            _requestDuration.postValue(elapsed)
        }
    }

    private fun toProtoBuf(measures: List<Measure>): ByteArray {
        val measuresProto = measures.map {

            val status = when (it.status) {
                Measure.Status.NEW -> MeasuresOuterClass.Status.NEW
                Measure.Status.OK -> MeasuresOuterClass.Status.OK
                Measure.Status.ERROR -> MeasuresOuterClass.Status.ERROR
            }


            MeasuresOuterClass.Measure.newBuilder()
                .setId(it.id)
                .setDate(it.date.timeInMillis)
                .setType(it.type.toString())
                .setStatus(status)
                .setValue(it.value)
                .build()
        }

        return MeasuresOuterClass.Measures.newBuilder()
            .addAllMeasures(measuresProto)
            .build().toByteArray()
    }

    private fun toJson(measures: List<Measure>): String {
        val gson = GsonBuilder()
            .registerTypeHierarchyAdapter(Calendar::class.java, CalendarTypeAdapter())
            .create()
        return gson.toJson(measures)
    }

    private fun fromJson(json: String): Map<Int, ResponseMessage> {
        val gson = Gson()
        return gson.fromJson(json, Array<ResponseMessage>::class.java).associateBy { it.id }
    }

    private fun toXML(measures: List<Measure>): String {
        val docType = DocType("measures", dtd)
        try {
            val root = org.jdom2.Element("measures")
            val document = org.jdom2.Document(root, docType)
            document.rootElement.addContent(measures.map { measure ->
                val measureElement = org.jdom2.Element("measure")
                measureElement.setAttribute("id", measure.id.toString())
                measureElement.setAttribute("status", measure.status.toString())
                measureElement.addContent(
                    org.jdom2.Element("type").setText(measure.type.toString())
                )
                measureElement.addContent(
                    org.jdom2.Element("value").setText(measure.value.toString())
                )
                measureElement.addContent(
                    org.jdom2.Element("date").setText(measure.date.toString())
                )
                measureElement
            })
            return org.jdom2.output.XMLOutputter().outputString(document)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return ""
    }

    private fun fromXML(xml: InputSource): Map<Int, ResponseMessage> {
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