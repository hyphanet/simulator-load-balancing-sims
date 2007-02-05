package sim;
import sim.clients.SimplePublisher;

class Sim implements EventTarget
{
	private final int NODES = 100; // Number of nodes
	private final int DEGREE = 5; // Average degree
	private final double FAST = 15000; // Speed of fast nodes, bytes/second
	private final double SLOW = 5000; // Speed of slow nodes, bytes/second
	private final double LATENCY = 0.1; // Latency of all links in seconds
	private Node[] nodes;
	
	public void run (double rate)
	{
		Network.reorder = true;
		Network.lossRate = 0.001;
		
		// Create the nodes - ten percent are slow
		nodes = new Node[NODES];
		for (int i = 0; i < NODES; i++) {
			double location = (double) i / NODES;
			if (Math.random() < 0.9)
				nodes[i] = new Node (location, FAST, FAST);
			else nodes[i] = new Node (location, SLOW, SLOW);
		}
		// Connect the nodes
		makeKleinbergNetwork();
		// One in ten nodes is a publisher, each with ten readers
		for (int i = 0; i < NODES; i += 10) {
			SimplePublisher pub
				= new SimplePublisher (rate, 0, nodes[i]);
			int readers = 0;
			while (readers < 10) {
				int index = (int) (Math.random() * NODES);
				if (index == i) continue;
				if (pub.addReader (nodes[index])) readers++;
			}
		}
		// Reset the counters after the first hour
		Event.schedule (this, 3600.0, RESET_COUNTERS, null);
		// Run the simulation
		Event.duration = 10800.0;
		Event.run();
		// Print the copiously detailed results
		System.out.println (Node.succeededLocally + " "
			+ Node.succeededRemotely + " " + Node.failed);
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
	
	private static void usage()
	{
		System.err.println ("Usage: Sim <load> <tokens> <backoff> <throttle>");
		System.exit (1);
	}
	
	public static void main (String[] args)
	{
		if (args.length != 4) usage();
		double load = Double.parseDouble (args[0]);
		Node.useTokens = Boolean.parseBoolean (args[1]);
		Node.useBackoff = Boolean.parseBoolean (args[2]);
		Node.useThrottle = Boolean.parseBoolean (args[3]);
		if (load <= 0.0) usage();
		new Sim().run (load / 60.0);
	}
	
	public void handleEvent (int type, Object data)
	{
		if (type == RESET_COUNTERS) {
			Node.succeededLocally = 0;
			Node.succeededRemotely = 0;
			Node.failed = 0;
		}
	}
	
	private final static int RESET_COUNTERS = 1;
}
