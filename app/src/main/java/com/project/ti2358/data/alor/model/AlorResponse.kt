package com.project.ti2358.data.alor.model

//{
//    "message": "Request with such X-ALOR-REQID was already handled. We return the response to that request.",
//    "oldResponse": {
//    "statusCode": 400,
//    "body": "Provided json can't be properly deserialised, perhaps you made an error or forgot some field"
//    "orderNumber": "18946416113"
//}
//}

data class AlorResponse(
    val message: String,
    val oldResponse: AlorResponseOld,
    val orderNumber: String?,
)