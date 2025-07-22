package com.bigshotsoftware.thenet.p2p.bootstrap

/**
 * Interface for bootstrap node selection strategies
 */
interface BootstrapNodeSelector {
    /**
     * Select bootstrap nodes from the available pool
     * @param nodes Available bootstrap nodes
     * @param count Number of nodes to select
     * @return Selected bootstrap nodes
     */
    fun selectNodes(nodes: List<BootstrapNode>, count: Int): List<BootstrapNode>
}

/**
 * Round-robin selection strategy
 */
class RoundRobinSelector : BootstrapNodeSelector {
    private var currentIndex = 0

    override fun selectNodes(nodes: List<BootstrapNode>, count: Int): List<BootstrapNode> {
        if (nodes.isEmpty()) return emptyList()

        val availableNodes = nodes.filter { it.isAvailable }
        if (availableNodes.isEmpty()) return emptyList()

        val selected = mutableListOf<BootstrapNode>()
        val startIndex = currentIndex % availableNodes.size

        for (i in 0 until minOf(count, availableNodes.size)) {
            val index = (startIndex + i) % availableNodes.size
            selected.add(availableNodes[index])
        }

        currentIndex = (startIndex + selected.size) % availableNodes.size
        return selected
    }
}

/**
 * Priority-based selection strategy
 */
class PrioritySelector : BootstrapNodeSelector {
    override fun selectNodes(nodes: List<BootstrapNode>, count: Int): List<BootstrapNode> {
        return nodes
            .filter { it.isAvailable }
            .sortedWith(
                compareByDescending<BootstrapNode> { it.priority }
                    .thenByDescending { it.successRate }
                    .thenBy { it.averageResponseTime },
            )
            .take(count)
    }
}

/**
 * Geographic proximity-based selection strategy
 */
class ProximitySelector(private val userRegion: String?) : BootstrapNodeSelector {
    override fun selectNodes(nodes: List<BootstrapNode>, count: Int): List<BootstrapNode> {
        val availableNodes = nodes.filter { it.isAvailable }

        return if (userRegion != null) {
            val (sameRegion, differentRegion) = availableNodes.partition { it.region == userRegion }
            (sameRegion + differentRegion).take(count)
        } else {
            availableNodes.take(count)
        }
    }
}

/**
 * Composite selector that tries multiple strategies in order
 */
class CompositeSelector(
    private val selectors: List<BootstrapNodeSelector>,
) : BootstrapNodeSelector {
    override fun selectNodes(nodes: List<BootstrapNode>, count: Int): List<BootstrapNode> {
        val selected = mutableSetOf<BootstrapNode>()

        for (selector in selectors) {
            if (selected.size >= count) break

            val additionalNodes = selector.selectNodes(
                nodes.filter { it !in selected },
                count - selected.size,
            )
            selected.addAll(additionalNodes)
        }

        return selected.toList()
    }
}
