package com.ambulance.driver.data

data class Hospital(
    val name: String,
    val latitude: Double,
    val longitude: Double
) {
    override fun toString(): String = name
}

object SampleHospitals {
    val all: List<Hospital> = listOf(
        Hospital("AIIMS New Delhi", 28.5672, 77.2100),
        Hospital("KEM Hospital, Mumbai", 19.0010, 72.8418),
        Hospital("Apollo Hospitals, Chennai", 13.0633, 80.2518),
        Hospital("NIMHANS, Bengaluru", 12.9432, 77.5963)
    )
}
