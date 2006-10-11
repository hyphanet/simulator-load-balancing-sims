class Sim
{
	private final static int NODES = 1000;
	private final static int DEGREE = 20;
	private final static int DEATHS = 30;
	private final static int TRIALS = 1000;
	
	private Node[] nodes;
	
	public Sim()
	{
		nodes = new Node[NODES];
		for (int i = 0; i < NODES; i++)
			nodes[i] = new Node (1.0 / NODES * i);
		
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
						nodes[i].connect (nodes[j]);
						nodes[j].connect (nodes[i]);
						break;
					}
				}
			}
		}
	}
	
	// Remove a randomly chosen node from the network
	private void killRandomNode()
	{
		int i = (int) (Math.random() * NODES);
		while (nodes[i] == null) i = (int) (Math.random() * NODES);
		nodes[i].die();
		nodes[i] = null;
	}
	
	// Remove the node with the longest links from the network
	private void killBestNode()
	{
		int bestIndex = 0;
		double bestDistance = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < NODES; i++) {
			if (nodes[i] == null) continue;
			double d = nodes[i].totalDistance();
			// double d = nodes[i].longestDistance();
			if (d > bestDistance) {
				bestIndex = i;
				bestDistance = d;
			}
		}
		nodes[bestIndex].die();
		nodes[bestIndex] = null;
	}
	
	// Route a message from a random source to a random destination, without
	// backtracking. If the message arrives, return the number of hops. If
	// the message does not arrive, return zero minus the number of hops.
	private int route()
	{
		Node n = null, dest = null;
		while (n == null) n = nodes[(int) (Math.random() * NODES)];
		while (dest == null) dest = nodes[(int) (Math.random() *NODES)];
		int hops;
		for (hops = 0; n != dest && n != null; hops++)
			n = n.route (dest);
		if (n == null) return -hops;
		else return hops;
	}
	
	// Return the Kleinberg lattice distance between a and b
	private int latticeDistance (int a, int b)
	{
		if (a > b) return Math.min (a - b, b - a + NODES);
		else return Math.min (b - a, a - b + NODES);
	}
	
	public static void main (String[] args)
	{
		double successes = 0.0;
		double successHops = 0.0;
		double failureHops = 0.0;
		Sim s = new Sim();
		
		for (int i = 0; i < DEATHS; i++) s.killBestNode();
		// for (int i = 0; i < DEATHS; i++) s.killRandomNode();
		
		for (int i = 0; i < TRIALS; i++) {
			int hops = s.route();
			if (hops >= 0) {
				successes++;
				successHops += hops;
			}
			else failureHops -= hops;
		}
		System.out.println ("success rate " + (successes/TRIALS));
		System.out.println ("success hops " + (successHops/successes));
		System.out.println ("failure hops " + (failureHops/(TRIALS-successes)));
	}
}
