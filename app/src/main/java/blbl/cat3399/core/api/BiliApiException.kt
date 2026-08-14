package blbl.cat3399.core.api

import java.io.IOException

class BiliApiException(
    val apiCode: Int,
    val apiMessage: String,
) : IOException("Bili API code=$apiCode message=$apiMessage")

