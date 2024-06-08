package com.andi.driver.behavior

import java.util.*

data class DetectionData(
    var id: String,
    val userId: String,
    val cls: String,
    val createdAt: String,
    val updatedAt: String
) {
    constructor() : this("", "", "", "", "")


}
