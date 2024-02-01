package org.srcgll.parser

import org.srcgll.RecoveryMode
import org.srcgll.descriptors.Descriptor
import org.srcgll.gss.GssNode
import org.srcgll.input.IGraph
import org.srcgll.input.ILabel
import org.srcgll.parser.context.IContext
import org.srcgll.rsm.RsmState
import org.srcgll.rsm.symbol.Nonterminal
import org.srcgll.sppf.node.SppfNode
import org.srcgll.sppf.node.SymbolSppfNode

/**
 * Interface for Gll parser with helper functions and main parsing loop
 */
interface GllParser<VertexType, LabelType : ILabel> {
    val ctx: IContext<VertexType, LabelType>

    fun parse(): Pair<SppfNode<VertexType>?, HashMap<Pair<VertexType, VertexType>, Int>> {
        initDescriptors(ctx.input)

        // Continue parsing until all default descriptors processed
        while (!ctx.descriptors.defaultDescriptorsStorageIsEmpty()) {
            parse(ctx.descriptors.next())
        }

        // If string was not parsed - process recovery descriptors until first valid parse tree is found
        // Due to the Error Recovery algorithm used it will be parse tree of the string with min editing cost
        if (ctx.recovery == RecoveryMode.ON) {
            while (ctx.parseResult == null) {
                parse(ctx.descriptors.next())
            }
        }
        return Pair(ctx.parseResult, ctx.reachabilityPairs)
    }

    fun parse(curDescriptor: Descriptor<VertexType>) {}

    /**
     *
     */
    fun initDescriptors(input: IGraph<VertexType, LabelType>) {
        for (startVertex in input.getInputStartVertices()) {
            val descriptor = Descriptor(
                ctx.startState,
                getOrCreateGssNode(ctx.startState.nonterminal, startVertex, weight = 0),
                sppfNode = null,
                startVertex
            )
            addDescriptor(descriptor)
        }
    }

    fun getOrCreateGssNode(
        nonterminal: Nonterminal,
        inputPosition: VertexType,
        weight: Int,
    ): GssNode<VertexType> {
        val gssNode = GssNode(nonterminal, inputPosition, weight)

        if (ctx.createdGssNodes.containsKey(gssNode)) {
            if (ctx.createdGssNodes.getValue(gssNode).minWeightOfLeftPart > weight) {
                ctx.createdGssNodes.getValue(gssNode).minWeightOfLeftPart = weight
            }
        } else ctx.createdGssNodes[gssNode] = gssNode

        return ctx.createdGssNodes.getValue(gssNode)
    }

    fun createGssNode(
        nonterminal: Nonterminal,
        state: RsmState,
        gssNode: GssNode<VertexType>,
        sppfNode: SppfNode<VertexType>?,
        pos: VertexType,
    ): GssNode<VertexType> {
        val newNode =
            getOrCreateGssNode(nonterminal, pos, weight = gssNode.minWeightOfLeftPart + (sppfNode?.weight ?: 0))

        if (newNode.addEdge(state, sppfNode, gssNode)) {
            if (ctx.poppedGssNodes.containsKey(newNode)) {
                for (popped in ctx.poppedGssNodes[newNode]!!) {
                    val descriptor = Descriptor(
                        state, gssNode, ctx.sppf.getParentNode(state, sppfNode, popped!!), popped.rightExtent
                    )
                    addDescriptor(descriptor)
                }
            }
        }

        return newNode
    }

    /**
     *
     */
    fun addDescriptor(descriptor: Descriptor<VertexType>) {
        val sppfNode = descriptor.sppfNode
        val state = descriptor.rsmState
        val leftExtent = sppfNode?.leftExtent
        val rightExtent = sppfNode?.rightExtent

        if (ctx.parseResult == null && sppfNode is SymbolSppfNode<*> && state.nonterminal == ctx.startState.nonterminal && ctx.input.isStart(
                leftExtent!!
            ) && ctx.input.isFinal(rightExtent!!)
        ) {
            ctx.descriptors.removeFromHandled(descriptor)
        }
        ctx.descriptors.addToHandling(descriptor)
    }
}