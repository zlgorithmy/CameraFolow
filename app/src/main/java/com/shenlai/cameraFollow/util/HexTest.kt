package com.shenlai.cameraFollow.util

object HexTest {

    private val HexCharArr = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')

    fun byteArrToHex(btArr: ByteArray): String {
        val strArr = CharArray(btArr.size * 2)
        var i = 0
        for (bt in btArr) {
            strArr[i++] = HexCharArr[bt.toInt() ushr 4 and 0xf]
            strArr[i++] = HexCharArr[bt.toInt() and 0xf]
        }
        return String(strArr)
    }
}