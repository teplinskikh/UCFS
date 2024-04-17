package org.ucfs.parser.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import org.ucfs.grammar.combinator.Grammar
import org.ucfs.rsm.symbol.ITerminal
import java.nio.file.Path

/**
 * Generator for a parser that uses a third-party lexer.
 * Unlike the scannerless parser , it uses scanner enumeration objects as terminals.
 * @see ScanerlessParserGenerator
 */
class  ParserGenerator(override val grammarClazz: Class<*>, private val terminalsEnum: Class<*>) : IParserGenerator {

    init{
        buildGrammar(grammarClazz)
    }
    override fun generateTerminalHandling(terminal: ITerminal): CodeBlock {

        val terminalName = "${terminalsEnum.simpleName}.$terminal"
        return CodeBlock.of(
            "%L(%L, %L, %L, %L, %L)",
            IParserGenerator.HANDLE_TERMINAL,
            terminalName,
            IParserGenerator.STATE_NAME,
            IParserGenerator.INPUT_EDGE_NAME,
            IParserGenerator.DESCRIPTOR,
            IParserGenerator.SPPF_NODE
        )
    }

    override fun getFileBuilder(location: Path, pkg: String): FileSpec.Builder {
        val builder = super.getFileBuilder(location, pkg)
        builder.addImport(terminalsEnum.packageName, terminalsEnum.simpleName)
        return builder
    }
    override val grammar: Grammar = buildGrammar(grammarClazz)
}