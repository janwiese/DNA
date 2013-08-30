package dna.graph.generators.undirected;

import dna.graph.Graph;
import dna.graph.datastructures.GraphDataStructure;
import dna.graph.edges.UndirectedEdge;
import dna.graph.generators.IRandomGenerator;
import dna.graph.nodes.Node;
import dna.util.Rand;
import dna.util.parameters.Parameter;

public class UndirectedRandomGraphGenerator extends UndirectedGraphGenerator
implements IRandomGenerator {
	public UndirectedRandomGraphGenerator(String name, Parameter[] params,
			GraphDataStructure gds, long timestampInit, int nodesInit,
			int edgesInit) {
		super(name, params, gds, timestampInit, nodesInit, edgesInit);
	}
	
	@Override
	public Graph generate() {
		Graph graph = this.newGraphInstance();

		for (int i = 0; i < this.nodesInit; i++) {
			Node node = this.gds.newNodeInstance(i);
			graph.addNode(node);
		}

		while (graph.getEdgeCount() < this.edgesInit) {
			int src = Rand.rand.nextInt(graph.getNodeCount());
			int dst = Rand.rand.nextInt(graph.getNodeCount());
			if (src != dst) {
				UndirectedEdge edge = (UndirectedEdge) this.gds.newEdgeInstance(
						graph.getNode(src), graph.getNode(dst));
				graph.addEdge(edge);
				edge.getNode1().addEdge(edge);
				edge.getNode2().addEdge(edge);
			}
		}

		return graph;
	}	
	
}