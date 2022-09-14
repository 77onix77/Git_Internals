package gitinternals

import java.io.File
import java.util.zip.Inflater
import kotlinx.datetime.*

fun main() {
    println("Enter .git directory location:")
    val path = readln()
    println("Enter git object hash:")
    val hash = readln()
    val fullPath = "$path\\objects\\${hash.substring(0, 2)}\\${hash.substring(2)}"
    val file = File(fullPath)
    val arrayByte = file.readBytes()
    val decompressor = Inflater()
    decompressor.setInput(arrayByte)
    val res = ByteArray(1000)
    val str = decompressor.inflate(res)
    decompressor.end()
    val content = String(res,0, str).replace(Char(0), '\n')
    val firstString = content.substringBefore('\n').split(" ")
    println("type:${firstString[0]} length:${firstString[1]}")


}
