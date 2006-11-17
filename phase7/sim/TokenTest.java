package sim;

class TokenTest
{
	public static void main (String[] args)
	{
		int NODES = 10; // Number of nodes
		double SPEED = 15000; // Network speed, bytes per second
		double LATENCY = 0.1; // Latency of all links in seconds
		
		Network.reorder = true;
		Network.lossRate = 0.001;
		
		// Create the nodes
		Node[] nodes = new Node[NODES];
		for (int i = 0; i < NODES; i++)
			nodes[i] = new Node (1.0 / NODES * i, SPEED, SPEED);
		// Connect the nodes
		for (int i = 0; i < NODES; i++) {
			nodes[i].connectBothWays (nodes[(i+1)%NODES], LATENCY);
			nodes[i].connectBothWays (nodes[(i+2)%NODES], LATENCY);
		}
		// Insert and request ten keys
		for (int i = 0; i < 600; i += 60) {
			int key = Node.locationToKey (Math.random());
			Event.schedule (nodes[0], i + 10,
					Node.INSERT_CHK, key);
			Event.schedule (nodes[NODES/2], i + 25,
					Node.REQUEST_CHK, key);
			key = Node.locationToKey (Math.random());
			Event.schedule (nodes[0], i + 40,
					Node.INSERT_SSK, key);
			Event.schedule (nodes[NODES/2], i + 55,
					Node.REQUEST_SSK, key);
		}
		// Run the simulation
		Event.run();
	}
}
