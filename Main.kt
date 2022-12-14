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

fun commit(listStr: MutableList<String>): String {
    var outData = "*COMMIT*\n"
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
    for (i in 1 until listStr.lastIndex) {
        if (listStr[i].isEmpty()) {
           outData += "commit message:\n"
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
        outData += "$str\n"
    }
    return outData.trim()
}

fun timeMod(str: String): String {
    val list = str.split(" ")
    val t = Instant.ofEpochSecond(list[list.lastIndex - 1].toLong()).atZone(ZoneOffset.of(list[list.lastIndex])).format(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss xxx")
    )
    var time = t.toString()
    time = time.replace("T", " ")
    time = time.replace("Z", "")
    return if (list[0] == "author:") "original timestamp: $time"
    else "commit timestamp: $time"
}

fun tree(byteArray: ByteArray): String {
    var outData = "*TREE*\n"
    var char0 = 0
    var res = ""
    var option = 0
    var count = 0
    val size = mutableListOf<String>()
    val name = mutableListOf<String>()
    val hash = mutableListOf<String>()
    for (byte in byteArray) {
        if (byte.toInt().toChar() == Char(0)) {
            char0++
            if (char0 == 1) {
                option = 1
                continue
            }
        }
        if (char0 == 0) continue
        when (option) {
            1 -> {
                if (byte.toInt().toChar() == ' ') {
                    option = 2
                    size.add(res)
                    res = ""
                    continue
                } else res += byte.toInt().toChar()
            }
            2 -> {
                if (byte.toInt().toChar() == Char(0)) {
                    option = 3
                    name.add(res)
                    res = ""
                    continue
                } else res += byte.toInt().toChar()
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
       outData +=  "${size[i]} ${hash[i]} ${name[i]}\n"
    }
    return outData.trim()
}

fun main() {
    println("Enter .git directory location:")
    val path = readln()
    println("Enter command:")
    when (readln()) {
        "cat-file" -> catFile(path)
        "list-branches" -> listBranches(path)
        "log" -> log(path)
        "commit-tree" -> commitTree(path)
    }
}

fun commitTree(path: String) {
    println("Enter commit-hash:")
    val hash = readln()
    val fullPath = "$path\\objects\\${hash.substring(0, 2)}\\${hash.substring(2)}"
    val listStr = decompressToString(fullPath)
    var hashTree =" "
    for(el in listStr) {
        if (el. contains("tree ")) {
            hashTree = el.substringAfter(" ").trim()
            break
        }
    }
    treeRecursive(path, hashTree)
}

fun treeRecursive (path: String, hash: String, dir: String = "") {
    val fullPath = "$path\\objects\\${hash.substring(0, 2)}\\${hash.substring(2)}"
    val tree = tree(decomressToByte(fullPath)).split("\n")
    for (i in 1..tree.lastIndex) {
        val list = tree[i].split(" ")
        if (list[0] == "100644") println(if (dir.isNotEmpty()) "$dir/${list[2]}" else list[2])
        if (list[0] == "40000") treeRecursive(path, list[1], if (dir.isNotEmpty()) "$dir/${list[2]}" else list[2])
    }
}

fun log(path: String) {
    println("Enter branch name:")
    val name = readln()
    val hash = File("$path\\refs\\heads\\$name").readText().trim()
    logCommit(path,hash)
}

fun logCommit(path: String, hash: String, merged: Boolean = false) {
    val fullPath = "$path\\objects\\${hash.substring(0, 2)}\\${hash.substring(2)}"
    val listStr = decompressToString(fullPath)
    val hashParents = mutableListOf<String>()
    var strCommiter = ""
    var coment = ""
    for(el in listStr) {
        if (el.contains("parent ")) hashParents += el.substringAfter(" ").trim()
        if (el.contains("committer ")) strCommiter = el.substringAfter("committer ")
        if (el.isEmpty()) {
            for ( i in listStr.indexOf(el) + 1..listStr.lastIndex) coment += "${listStr[i]}\n"
        break
        }
    }
    strCommiter = strCommiter.replace("<", "")
    strCommiter = strCommiter.replace(">", "")
    strCommiter = strCommiter.replace(Regex("\\d{10} [-|+]\\d{4}"), timeMod(strCommiter))
    println("Commit: $hash" + if (merged) " (merged)" else "")
    println(strCommiter)
    println(coment.trim())
    println()
    //println(content)
    if (hashParents.size > 1) logCommit(path,hashParents[1], true)
    if (hashParents.isNotEmpty() && !merged) {
        logCommit(path, hashParents[0])
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
    val listStr = decompressToString(fullPath)
    if (listStr[0].contains("blob")) blob(listStr)
    if (listStr[0].contains("commit")) println(commit(listStr))
    if (listStr[0].contains("tree")) println(tree(decomressToByte(fullPath)))
}

fun decompressToString(path: String): MutableList<String> {
    val file = File(path)
    val arrayByte = file.readBytes()
    val decompressor = Inflater()
    decompressor.setInput(arrayByte)
    val res = ByteArray(1000)
    val str = decompressor.inflate(res)
    decompressor.end()
    val content = String(res,0, str).replace(Char(0), '\n')
    return content.split('\n').toMutableList()
}

fun decomressToByte(path: String): ByteArray {
    val file = File(path)
    val arrayByte = file.readBytes()
    val decompressor = Inflater()
    decompressor.setInput(arrayByte)
    val res = ByteArray(1000)
    decompressor.inflate(res)
    decompressor.end()
    return res
}

