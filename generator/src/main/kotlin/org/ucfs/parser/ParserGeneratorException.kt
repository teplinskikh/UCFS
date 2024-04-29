package org.ucfs.parser

class ParserGeneratorException(msg: String? = "") : Exception("Parser generator exception: $msg") {
    companion object{
        val grammarExpectedMsg = "Only subclass of Grammar class can be used for parser generation"
    }
}