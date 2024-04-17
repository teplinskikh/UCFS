package dynamic.parser.generator

import dynamic.parser.IDynamicGllTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import org.ucfs.input.IInputGraph
import org.ucfs.input.ILabel
import org.ucfs.parser.generator.GeneratedParser
import kotlin.test.assertNotNull

interface IOfflineGllTest : IDynamicGllTest {
    /**
     * Test for any type of incorrect input
     * Parametrize parser with it's input before parsing
     */
    fun <VertexType, LabelType : ILabel> getIncorrectTestContainer(
        caseName: String,
        gll: GeneratedParser<VertexType, LabelType>,
        input: IInputGraph<VertexType, LabelType>
    ): DynamicNode {
        return DynamicTest.dynamicTest("[fail] $caseName") {
            gll.input = input
            val result = gll.parse().first
            Assertions.assertNull(result)
        }
    }

    /**
     * Test for any type of correct input
     * Parametrize parser with it's input before parsing
     */
    fun <VertexType, LabelType : ILabel> getCorrectTestContainer(
        caseName: String,
        gll: GeneratedParser<VertexType, LabelType>,
        input: IInputGraph<VertexType, LabelType>
    ): DynamicNode {
        return DynamicTest.dynamicTest("[ok] $caseName") {
            gll.input = input
            val result = gll.parse()
            if (result.first == null) {
                System.err.println("input: $input")
            }
            //TODO add check for parsing result quality
            assertNotNull(result.first)
        }
    }
}