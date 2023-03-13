package ch.heigvd.iict.dma.labo1.repositories

import ch.heigvd.iict.dma.labo1.models.Measure

data class ResponseMessage(val id : Int, val status : Measure.Status)
