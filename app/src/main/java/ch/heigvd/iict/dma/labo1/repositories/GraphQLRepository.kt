package ch.heigvd.iict.dma.labo1.repositories

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import ch.heigvd.iict.dma.labo1.models.*
import ch.heigvd.iict.dma.labo1.ui.graphql.adapters.AuthorAdapter
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.system.measureTimeMillis

class GraphQLRepository(
    private val scope: CoroutineScope,
    private val httpsUrl: String = "https://mobile.iict.ch/graphql"
) {

    private val _working = MutableLiveData(false)
    val working: LiveData<Boolean> get() = _working

    private val _authors = MutableLiveData<List<Author>>(mutableListOf())
    val authors: LiveData<List<Author>> get() = _authors

    private val _books = MutableLiveData<List<Book>>(mutableListOf())
    val books: LiveData<List<Book>> get() = _books

    private val _requestDuration = MutableLiveData(-1L)
    val requestDuration: LiveData<Long> get() = _requestDuration

    fun resetRequestDuration() {
        _requestDuration.postValue(-1L)
    }

    fun loadAllAuthorsList() {
        val gson = GsonBuilder().create()

        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main){
                _working.postValue(true)
            }

            val elapsed = measureTimeMillis {
                val jsonRequest = "{\"query\": \"query { findAllAuthors{ id, name }}\" }";


                val url = URL(httpsUrl)
                val httpURLConnection = url.openConnection() as HttpURLConnection
                httpURLConnection.requestMethod = "POST"
                httpURLConnection.setRequestProperty(
                    "Content-Type",
                    "application/json"
                ) // The format of the content we're sending to the server
                httpURLConnection.setRequestProperty(
                    "Accept",
                    "application/json"
                ) // The format of response we want to get from the server
                httpURLConnection.doInput = true
                httpURLConnection.doOutput = true

                // Send the JSON we created
                val outputStreamWriter = OutputStreamWriter(httpURLConnection.outputStream)
                outputStreamWriter.write(jsonRequest)
                outputStreamWriter.flush()

                // Check if the connection is successful
                val responseCode = httpURLConnection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = httpURLConnection.inputStream.bufferedReader()
                        .use { it.readText() }  // defaults to UTF-8
                    withContext(Dispatchers.Main) {
                        val typeToken = object : TypeToken<GraphQlData<ArrayList<Author>>>() {}.type
                        val authors = Gson().fromJson<GraphQlData<ArrayList<Author>>>(response, typeToken).data["findAllAuthors"]
                        _authors.postValue(authors!!)
                    }
                } else {
                    Log.e("HTTPURLCONNECTION_ERROR", responseCode.toString())
                }

            }
            withContext(Dispatchers.Main){
                _working.postValue(false)
            }
            _requestDuration.postValue(elapsed)
        }
    }

    fun loadBooksFromAuthor(author: Author) {

        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main){
                _working.postValue(true)
            }


            val elapsed = measureTimeMillis {
                val jsonRequest =
                    "{\"query\": \"query { findAuthorById(id:" + author.id + "){ books{ id, title, publicationDate, authors{ id, name, } } }}\" }"


                val url = URL(httpsUrl)
                val httpURLConnection = url.openConnection() as HttpURLConnection
                httpURLConnection.requestMethod = "POST"
                httpURLConnection.setRequestProperty(
                    "Content-Type",
                    "application/json"
                ) // The format of the content we're sending to the server
                httpURLConnection.setRequestProperty(
                    "Accept",
                    "application/json"
                ) // The format of response we want to get from the server
                httpURLConnection.doInput = true
                httpURLConnection.doOutput = true

                // Send the JSON we created
                val outputStreamWriter = OutputStreamWriter(httpURLConnection.outputStream)
                outputStreamWriter.write(jsonRequest)
                outputStreamWriter.flush()

                // Check if the connection is successful
                val responseCode = httpURLConnection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = httpURLConnection.inputStream.bufferedReader()
                        .use { it.readText() }  // defaults to UTF-8
                    withContext(Dispatchers.Main) {
                        val typeToken = object : TypeToken<GraphQlData<Author>>() {}.type
                        val books = Gson().fromJson<GraphQlData<Author>>(response, typeToken).data["findAuthorById"]?.books
                        _books.postValue(books!!)
                    }
                } else {
                    Log.e("HTTPURLCONNECTION_ERROR", responseCode.toString())
                }

            }
            withContext(Dispatchers.Main){
                _working.postValue(false)
            }
            _requestDuration.postValue(elapsed)
        }
    }

}