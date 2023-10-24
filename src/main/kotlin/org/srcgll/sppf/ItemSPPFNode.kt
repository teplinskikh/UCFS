package org.srcgll.sppf

import org.srcgll.grammar.RSMState
import java.util.*

class ItemSPPFNode <VertexType>
(
    val rsmState : RSMState,
    leftExtent   : VertexType,
    rightExtent  : VertexType,
)
    : ParentSPPFNode<VertexType>(leftExtent, rightExtent)
{
    override fun toString() =
        "ItemSPPFNode(leftExtent=$leftExtent, rightExtent=$rightExtent, rsmState=$rsmState)"

    override fun equals(other : Any?) : Boolean
    {
        if (this === other)             return true
        if (other !is ItemSPPFNode<*>)  return false
        if (!super.equals(other))       return false
        if (rsmState != other.rsmState) return false

        return true
    }

    override val hashCode : Int = Objects.hash(leftExtent, rightExtent, rsmState)
    override fun hashCode() = hashCode
}
