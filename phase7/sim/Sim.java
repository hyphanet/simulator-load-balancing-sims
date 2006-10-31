package sim;
import sim.generators.SimplePublisher;

class Sim
{
	private final int NODES = 100; // Number of nodes
	private final int DEGREE = 4; // Average degree
	private final double SPEED = 20000; // Bytes per second
	private final double LATENCY = 0.1; // Latency of all links in seconds
	private final double RATE = 0.01; // Inserts per second
	private Node[] nodes;
	
	public Sim()
	{
		Network.reorder = true;
		Network.lossRate = 0.001;
		
		// Create the nodes
		nodes = new Node[NODES];
		for (int i = 0; i < NODES; i++)
			nodes[i] = new Node (1.0 / NODES * i, SPEED, SPEED);
		// Connect the nodes
		makeKleinbergNetwork();
		
		// One publisher, ten randomly chosen readers
		SimplePublisher pub = new SimplePublisher (RATE, nodes[0]);
		int readers = 0;
		while (readers < 10) {
			int index = (int) (Math.random() * NODES);
			if (pub.addReader (nodes[index])) readers++;
		}
		
		// Run the simulation
		Event.duration = 3600.0;
		Event.run();
	}
	
	// Return the lattice distance between a and b
	private int latticeDistance (int a, int b)
	{
		if (a > b) return Math.min (a - b, b - a + NODES);
		else return Math.min (b - a, a - b + NODES);
	}
	
	private void makeKleinbergNetwork()
	{
		// Calculate the normalising constant
		double norm = 0.0;
		for (int i = 1; i < NODES; i++)
			norm += 1.0 / latticeDistance (0, i);
		
		// Add DEGREE shortcuts per node, randomly with replacement
		for (int i = 0; i < NODES; i++) {
			for (int j = 0; j < i; j++) {
				double p = 1.0 / latticeDistance (i, j) / norm;
				for (int k = 0; k < DEGREE; k++) {
					if (Math.random() < p) {
						nodes[i].connectBothWays
							(nodes[j], LATENCY);
						break;
					}
				}
			}
		}
	}
	
	public static void main (String[] args)
	{
		new Sim();
	}
}
