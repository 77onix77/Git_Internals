package gitinternals

import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.Inflater


fun blob(listStr: List<String>) {
    println("*BLOB*")
    for (i in 1..listStr.lastIndex) println(listStr[i])
}

fun commit(listStr: MutableList<String>) {
    println("*COMMIT*")
    var par = 0
    var par1 = 0
    val listInd = mutableListOf<Int>()
    for (i in 0..listStr.lastIndex) {
        if (listStr[i].contains("parent ")) {
            par++
            if (par == 1) {
                listStr[i] = listStr[i].replace("parent", "parents")
                par1 = i
            } else {
                listStr[par1] += " | ${listStr[i].substringAfter(" ")}"
                listInd += i
            }
        }
    }
    for (el in listInd) listStr.removeAt(el)
    for (i in 1..listStr.lastIndex - 1) {
        if (listStr[i].isEmpty()) {
            println("commit message:")
            continue
        }
        var str = listStr[i]
        str = str.replace("tree", "tree:")
        str = str.replace("parents", "parents:")
        str = str.replace("<", "")
        str = str.replace(">", "")
        str = str.replace("author", "author:")
        str = str.replace("committer", "committer:")
        if (str.contains(Regex("\\d{10} [-|+]\\d{4}"))) {
            str = str.replace(Regex("\\d{10} [-|+]\\d{4}"), timeMod(str))
        }
        println(str)
    }
}

fun timeMod(str: String): String {
    val list = str.split(" ")
    val t = Instant.ofEpochSecond(list[list.lastIndex - 1].toLong()).atZone(ZoneOffset.of(list[list.lastIndex])).format(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss xxx"))
    var time = t.toString()
    time = time.replace("T", " ")
    time = time.replace("Z", "")
    val res = if (list[0] == "author:") "original timestamp: $time"
    else "commit timestamp: $time"
    return res
}

fun tree(byteArray: ByteArray) {
    println("*TREE*")
    var char0 = 0
    var res = ""
    var option = 0
    var count = 0
    val size = mutableListOf<String>()
    val name = mutableListOf<String>()
    val hash = mutableListOf<String>()
    for (byte in byteArray) {
        if (byte.toChar() == Char(0)) {
            char0++
            if (char0 == 1) {
                option = 1
                continue
            }
        }
        if (char0 == 0) continue
        when (option) {
            1 -> {
                if (byte.toChar() == ' ') {
                    option = 2
                    size.add(res)
                    res = ""
                    continue
                } else res += byte.toChar()
            }
            2 -> {
                if (byte.toChar() == Char(0)) {
                    option = 3
                    name.add(res)
                    res = ""
                    continue
                } else res += byte.toChar()
            }
            3 -> {
                res += String.format("%02x", byte)
                count++
                if (count == 20) {
                    option = 1
                    hash.add(res)
                    res = ""
                    count = 0
                }
            }
        }
    }
    for (i in 0..name.lastIndex) {
        println("${size[i]} ${hash[i]} ${name[i]}")
    }
}

fun main() {
    println("Enter .git directory location:")
    val path = readln()
    println("Enter command:")
    val comand = readln()
    when (comand) {
        "cat-file" -> catFile(path)
        "list-branches" -> listBranches(path)
    }
}

fun listBranches(path: String) {
    val fullPath = "$path\\refs\\heads"
    val listFile = (File(fullPath).list()?.sorted())
    val head = File("$path\\HEAD").readText().split("/").last().trim()
    for (el in listFile!!) {
        if (el == head) println("* $el")
        else println("  $el")
    }
}

fun catFile(path: String) {
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
    val listStr = content.split('\n').toMutableList()
    if (listStr[0].contains("blob")) blob(listStr)
    if (listStr[0].contains("commit")) commit(listStr)
    if (listStr[0].contains("tree")) tree(res)

}
