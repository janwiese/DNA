package dna.graph.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import dna.graph.Graph;
import dna.graph.IElement;
import dna.graph.datastructures.DEmpty;
import dna.graph.datastructures.DataStructure.ListType;
import dna.graph.datastructures.GraphDataStructure;
import dna.graph.datastructures.IDataStructure;
import dna.graph.edges.DirectedEdge;
import dna.graph.edges.Edge;
import dna.graph.edges.UndirectedEdge;
import dna.graph.generators.GraphGenerator;
import dna.graph.generators.IGraphGenerator;
import dna.graph.generators.canonical.CliqueGraph;
import dna.graph.generators.canonical.RingGraph;
import dna.graph.generators.random.IRandomGenerator;
import dna.graph.generators.util.EmptyGraph;
import dna.graph.nodes.DirectedNode;
import dna.graph.nodes.Node;
import dna.graph.nodes.UndirectedNode;
import dna.graph.weights.IWeighted;
import dna.io.GraphReader;
import dna.io.GraphWriter;

@RunWith(Parallelized.class)
public class GeneratorsTest {
	private Class<? extends Node> nodeType;
	private Class<? extends Edge> edgeType;
	private Class<? extends IGraphGenerator> generator;
	private Constructor<? extends GraphGenerator> generatorConstructor;
	private GraphDataStructure gds;
	private GraphGenerator gg;

	private int nodeSize = 100;
	private int edgeSize = 120;

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	public GeneratorsTest(
			EnumMap<ListType, Class<? extends IDataStructure>> listTypes,
			Class<? extends Node> nodeType, Class<? extends Edge> edgeType,
			Class<? extends GraphGenerator> generator)
			throws InstantiationException, IllegalAccessException,
			IllegalArgumentException, InvocationTargetException,
			NoSuchMethodException, SecurityException {
		this.nodeType = nodeType;
		this.edgeType = edgeType;
		this.generator = generator;

		this.gds = new GraphDataStructure(listTypes, nodeType, edgeType);

		if (generator == CliqueGraph.class) {
			/**
			 * As clique graphs are large, generate a smaller one please!
			 */
			nodeSize = (int) Math.min(Math.floor(nodeSize / 2), 30);
			edgeSize = nodeSize * (nodeSize - 1);

			if (UndirectedNode.class.isAssignableFrom(nodeType))
				edgeSize = (int) edgeSize / 2;
		} else if (generator == RingGraph.class) {
			edgeSize = nodeSize;
		} else if (generator == EmptyGraph.class) {
			nodeSize = 0;
			edgeSize = 0;
		}

		try {
			this.generatorConstructor = generator.getConstructor(
					GraphDataStructure.class, int.class, int.class);
			this.gg = this.generatorConstructor.newInstance(gds, nodeSize,
					edgeSize);
		} catch (NoSuchMethodException e) {
			this.generatorConstructor = generator.getConstructor(
					GraphDataStructure.class, int.class);
			this.gg = this.generatorConstructor.newInstance(gds, nodeSize);
		}

		/**
		 * A short output to overcome the timeout of Travis: If there is no
		 * console output in 10 minutes, a test run is stopped
		 */
		if (Math.random() < 0.001)
			System.out.print(".");

	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Parameterized.Parameters(name = "{0} {1} {2} {3}")
	public static Collection testPairs() throws NoSuchMethodException,
			SecurityException, InstantiationException, IllegalAccessException,
			IllegalArgumentException, InvocationTargetException {
		Constructor<? extends GraphGenerator> generatorConstructor;
		GraphDataStructure gds;

		ArrayList<EnumMap<ListType, Class<? extends IDataStructure>>> simpleCombinations = GraphDataStructure
				.getSimpleDatastructureCombinations();

		ArrayList<Object> result = new ArrayList<>();
		for (EnumMap<ListType, Class<? extends IDataStructure>> combination : simpleCombinations) {
			for (Class generator : GlobalTestParameters.graphGenerators) {
				for (Class edgeType : new Class[]{DirectedEdge.class, UndirectedEdge.class}) {
					for (Class nodeType :  new Class[]{DirectedNode.class, UndirectedNode.class}) {
						if ((UndirectedEdge.class.isAssignableFrom(edgeType) && DirectedNode.class
								.isAssignableFrom(nodeType))
								|| (DirectedEdge.class
										.isAssignableFrom(edgeType) && UndirectedNode.class
										.isAssignableFrom(nodeType)))
							continue;

						if (combination.get(ListType.GlobalEdgeList) == DEmpty.class
								|| combination.get(ListType.LocalEdgeList) == DEmpty.class)
							continue;

						gds = new GraphDataStructure(combination, nodeType,
								edgeType);

						GraphGenerator gg;
						try {
							generatorConstructor = generator.getConstructor(
									GraphDataStructure.class, int.class,
									int.class);
							gg = generatorConstructor.newInstance(gds, 5, 5);
						} catch (NoSuchMethodException e) {
							generatorConstructor = generator.getConstructor(
									GraphDataStructure.class, int.class);
							gg = generatorConstructor.newInstance(gds, 5);
						}

						if (!gg.canGenerateNodeType(nodeType))
							continue;
						if (!gg.canGenerateEdgeType(edgeType))
							continue;

						result.add(new Object[] { combination, nodeType,
								edgeType, generator });
					}
				}
			}
		}

		return result;
	}

	@Test
	public void testGraphGeneration() {
		Graph g = gg.generate();

		assertEquals(nodeSize, g.getNodeCount());
		assertEquals(edgeSize, g.getEdgeCount());
	}

	@Test
	public void testWriteRead() throws ClassNotFoundException, IOException {
		Graph g = gg.generate();

		String graphName = gds.getDataStructures();

		HashFunction hf = Hashing.md5();
		HashCode hc = hf.newHasher().putString(graphName).hash();
		graphName = hc.toString();

		String tempFolder = folder.newFolder().getAbsolutePath();

		GraphWriter.write(g, tempFolder, graphName);
		Graph g2 = GraphReader.read(tempFolder, graphName, null);

		assertEquals(gds, g2.getGraphDatastructures());

		assertEquals(g, g2);
	}

	@Test
	public void testRandomGraphsAreRandom() {
		assumeTrue(IRandomGenerator.class.isAssignableFrom(generator));
		Graph g = gg.generate();

		for (int i = 0; i < 20; i++) {
			Graph g2 = gg.generate();
			assertNotEquals(g, g2);
		}
	}

	@Test
	public void writeWeightedReadUnweighted() throws ClassNotFoundException,
			IOException {
		assumeTrue(IWeighted.class.isAssignableFrom(nodeType));
		assumeTrue(IWeighted.class.isAssignableFrom(edgeType));

		Graph g = gg.generate();

		String graphName = gds.getDataStructures();
		String tempFolder = folder.newFolder().getAbsolutePath();

		HashFunction hf = Hashing.md5();
		HashCode hc = hf.newHasher().putString(graphName).hash();
		graphName = hc.toString();

		GraphWriter.write(g, tempFolder, graphName);

		if (UndirectedNode.class.isAssignableFrom(nodeType)) {
			gds.setNodeType(UndirectedNode.class);
			gds.setEdgeType(UndirectedEdge.class);
		} else if (DirectedNode.class.isAssignableFrom(nodeType)) {
			gds.setNodeType(DirectedNode.class);
			gds.setEdgeType(DirectedEdge.class);
		} else {
			fail("Unknown node type");
		}

		Graph g2 = GraphReader.read(tempFolder, graphName, gds);
		GraphWriter.write(g2, tempFolder, graphName + "new");

		/**
		 * Don't go the easy way and check for edge list sizes here - some may
		 * contain duplicates, some not, this might easily yield errors!
		 */

		for (IElement nU : g2.getNodes()) {
			Node n = (Node) nU;
			assertTrue(
					"Graph g misses node " + n + " (node list type: "
							+ gds.getListClass(ListType.GlobalNodeList) + ")",
					g.containsNode(n));
		}

		for (IElement nU : g.getNodes()) {
			Node n = (Node) nU;
			assertTrue(g2.containsNode(n));
		}

		for (IElement eU : g2.getEdges()) {
			Edge e = (Edge) eU;
			Edge eOther = g.getEdge(e);
			assertNotNull("Graph g misses edge " + e + " (edge list type: "
					+ gds.getListClass(ListType.GlobalEdgeList) + ")", eOther);
			assertEquals(e, eOther);
			assertNotEquals(e.getStringRepresentation(),
					eOther.getStringRepresentation());
		}

		for (IElement eU : g.getEdges()) {
			Edge e = (Edge) eU;
			Edge eOther = g2.getEdge(e);
			assertNotNull(eOther);
			assertEquals(e, eOther);
			assertNotEquals(e.getStringRepresentation(),
					eOther.getStringRepresentation());
		}

	}

	@Test
	public void testWriteReadWithErrorInEdge() throws ClassNotFoundException,
			IOException {
		/**
		 * Don't run this check on clique graphs, as they are too large to get
		 * compared multiple times. If anything is wrong that would be tested
		 * here, we would see it with other generators too
		 */
		assumeFalse(CliqueGraph.class.isAssignableFrom(generator));
		assumeFalse(EmptyGraph.class.isAssignableFrom(generator));

		Graph g = gg.generate();

		String graphName = gds.getDataStructures();
		String tempFolder = folder.newFolder().getAbsolutePath();

		HashFunction hf = Hashing.md5();
		HashCode hc = hf.newHasher().putString(graphName).hash();
		graphName = hc.toString();

		GraphWriter.write(g, tempFolder, graphName);

		Graph g2 = GraphReader.read(tempFolder, graphName, null);

		assertEquals(g, g2);

		// Change getStringRepresentation now to see that it is used for
		// equality checks
		Node random = g.getRandomNode();
		for (int i = 0; i < (int) Math.floor(edgeSize / 5); i++) {
			Edge edgeReal = g.getRandomEdge();
			assertNotNull(edgeReal);
			g.removeEdge(edgeReal);

			Edge edgeMocked = mock(this.gds.getEdgeType());
			when(edgeMocked.getStringRepresentation()).thenReturn("");
			when(edgeMocked.getHashString()).thenReturn("");
			if (gds.createsDirected()) {
				when(((DirectedEdge) edgeMocked).getDst()).thenReturn(
						(DirectedNode) random);
				when(((DirectedEdge) edgeMocked).getSrc()).thenReturn(
						(DirectedNode) random);
			} else if (gds.createsUndirected()) {
				when(((UndirectedEdge) edgeMocked).getNode1()).thenReturn(
						(UndirectedNode) random);
				when(((UndirectedEdge) edgeMocked).getNode2()).thenReturn(
						(UndirectedNode) random);
			}

			g.addEdge(edgeMocked);
			assertNotEquals(g, g2);
		}
	}

	@Test
	public void testWriteReadWithErrorInNode() throws ClassNotFoundException,
			IOException {
		assumeFalse(EmptyGraph.class.isAssignableFrom(generator));

		Graph g = gg.generate();

		String graphName = gds.getDataStructures();
		String tempFolder = folder.newFolder().getAbsolutePath();

		HashFunction hf = Hashing.md5();
		HashCode hc = hf.newHasher().putString(graphName).hash();
		graphName = hc.toString();

		GraphWriter.write(g, tempFolder, graphName);

		Graph g2 = GraphReader.read(tempFolder, graphName, null);

		assertEquals(g, g2);

		// Change getStringRepresentation now to see that it is used for
		// equality checks
		Node nodeReal = g.getNode(g.getNodeCount() - 1);
		assertNotNull(nodeReal);
		g.removeNode(nodeReal);
		Node nodeMocked = mock(this.nodeType);
		when(nodeMocked.getStringRepresentation()).thenReturn("");
		g.addNode(nodeMocked);
		assertNotEquals(g, g2);
	}

}
