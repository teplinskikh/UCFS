import com.sun.tools.javac.Main
import org.antlr.Java8Lexer
import org.antlr.Java8Parser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.junit.jupiter.api.Test
import java.io.File

fun main(args: Array<String>){
    try {
        AntlrBenchmark().parse(File(args[0]).readText())
    }
    catch (e: Throwable){
        println(e)
        System.exit(1)
    }
}
class AntlrBenchmark : ParsingBenchmarks() {

    override fun getShortName(): String = "Antlr"



    @Override
    override fun parse(text: String) {
        var x = 30
        while(x-- > 0) {
            val antlrParser = Java8Parser(
                CommonTokenStream(
                    Java8Lexer(
                        CharStreams.fromString(text)
                    )
                )
            )
            try {
                antlrParser.compilationUnit()
            } catch (e: Exception) {
                print(e)
            }
        }
    }

    @Test
    fun t() {
        parse("""/**
 * Copyright (c) 2016-present, RxJava Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */

package io.reactivex.exceptions;

/**
 * Wrapper for Throwable errors that are sent to `RxJavaPlugins.onError`.
 * <p>History: 2.0.6 - experimental; 2.1 - beta
 * @since 2.2
 */
public final class UndeliverableException extends IllegalStateException {

    private static final long serialVersionUID = 1644750035281290266L;

    /**
     * Construct an instance by wrapping the given, non-null
     * cause Throwable.
     * @param cause the cause, not null
     */
    public UndeliverableException(Throwable cause) {
        super("The exception could not be delivered to the consumer because it has already canceled/disposed the flow or the exception has nowhere to go to begin with. Further reading: https://github.com/ReactiveX/RxJava/wiki/What's-different-in-2.0#error-handling | " + cause, cause);
    }
}""")
    }


}

