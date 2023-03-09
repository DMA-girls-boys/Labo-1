package ch.heigvd.iict.dma.labo1.models

enum class Serialisation {
    JSON, XML, PROTOBUF
}

enum class NetworkType {
    RANDOM, CSD, GPRS, EDGE, UMTS, HSPA, LTE, NR5G;

    override fun toString(): String {
        return when (this) {
            RANDOM -> "Random"
            CSD -> "CSD"
            GPRS -> "GPRS"
            EDGE -> "EDGE"
            UMTS -> "UMTS"
            HSPA -> "HSPA"
            LTE -> "LTE"
            NR5G -> "NR5G"
        }
    }
}

enum class Compression {
    DISABLED, DEFLATE
}

enum class Encryption {
    DISABLED, SSL
}