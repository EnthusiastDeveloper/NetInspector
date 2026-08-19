package dev.enthusiastdev.netinspector.core.model.wifi

enum class Band { GHZ_2_4, GHZ_5, GHZ_6, UNKNOWN }

/** design §6.2 - covers both special cases that trip up naive implementations. */
fun bandOf(mhz: Int): Band =
    when (mhz) {
        in 2400..2500 -> Band.GHZ_2_4
        in 5150..5900 -> Band.GHZ_5
        in 5925..7125 -> Band.GHZ_6
        else -> Band.UNKNOWN
    }

/** design §6.2 - channel 14 and 6 GHz channel 2 (5935 MHz) are the special cases. */
fun freqToChannel(mhz: Int): Int? =
    when (mhz) {
        2484 -> 14
        in 2412..2472 -> (mhz - 2407) / 5
        in 5160..5895 -> (mhz - 5000) / 5
        5935 -> 2
        in 5955..7115 -> (mhz - 5950) / 5
        else -> null
    }
