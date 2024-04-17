package org.ucfs.parser.generator

import org.ucfs.descriptors.Descriptor
import org.ucfs.grammar.combinator.Grammar
import org.ucfs.input.Edge
import org.ucfs.input.IInputGraph
import org.ucfs.input.ILabel
import org.ucfs.parser.IGll
import org.ucfs.parser.ParsingException
import org.ucfs.parser.context.Context
import org.ucfs.parser.context.IContext
import org.ucfs.rsm.RsmState
import org.ucfs.rsm.symbol.ITerminal
import org.ucfs.rsm.symbol.Nonterminal
import org.ucfs.sppf.node.SppfNode

abstract class GeneratedParser<VertexType, LabelType : ILabel> :
    IGll<VertexType, LabelType> {
    abstract val grammar: Grammar

    var input: IInputGraph<VertexType, LabelType>
        get() {
            return ctx.input
        }
        set(value) {
            ctx = Context(grammar.rsm, value)
        }

    protected abstract val ntFuncs: HashMap<Nonterminal, (Descriptor<VertexType>, SppfNode<VertexType>?) -> Unit>

    override fun parse(descriptor: Descriptor<VertexType>) {
        val state = descriptor.rsmState
        val nt = state.nonterminal

        val handleEdges = ntFuncs[nt] ?: throw ParsingException("Nonterminal ${nt.name} is absent from the grammar!")

        val pos = descriptor.inputPosition

        ctx.descriptors.addToHandled(descriptor)
        val curSppfNode = descriptor.sppfNode
        val epsilonSppfNode = ctx.sppf.getEpsilonSppfNode(descriptor)

        val leftExtent = curSppfNode?.leftExtent
        val rightExtent = curSppfNode?.rightExtent

        if (state.isFinal) {
            pop(descriptor.gssNode, curSppfNode ?: epsilonSppfNode, pos)
        }

        if (state.isStart && state.isFinal) {
            checkAcceptance(epsilonSppfNode, epsilonSppfNode!!.leftExtent, epsilonSppfNode!!.rightExtent, state.nonterminal)
        }
        checkAcceptance(curSppfNode, leftExtent, rightExtent, state.nonterminal)

        for (inputEdge in ctx.input.getEdges(pos)) {
            if (inputEdge.label.terminal == null) {
                handleTerminalOrEpsilonEdge(descriptor, curSppfNode, null, descriptor.rsmState, inputEdge.head, 0)
                continue
            }
        }
        handleEdges(descriptor, curSppfNode)
    }

    protected fun handleTerminal(
        terminal: ITerminal,
        state: RsmState,
        inputEdge: Edge<VertexType, LabelType>,
        descriptor: Descriptor<VertexType>,
        curSppfNode: SppfNode<VertexType>?
    ) {

        val newStates = state.terminalEdges[terminal] ?:
            throw ParsingException("State $state does not contains edges " +
                    "\nby terminal $terminal" +
                    "\naccessible edges: ${state.terminalEdges}\n")


        if (inputEdge.label.terminal == terminal) {
            for (target in newStates) {
                handleTerminalOrEpsilonEdge(
                    descriptor,
                    curSppfNode,
                    terminal,
                    target,
                    inputEdge.head,
                    0
                )
            }
        }
    }
}