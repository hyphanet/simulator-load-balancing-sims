class Sim
{
	public static Node[] makeKleinbergNetwork (int n, int k, double speed)
	{
		Node[] nodes = new Node[n];
		for (int i = 0; i < n; i++)
			nodes[i] = new Node (1.0 / n * i, speed, speed);
		int m = 0; // Number of directed edges
		while (m < n * k) {
			Node src = nodes[(int)(Math.random() * n)];
			Node dest = nodes[(int)(Math.random() * n)];
			double d = Node.distance (src.location, dest.location);
			if (Math.random() * 0.5 < 0.5 - d)
				if (src.connectBothWays (dest, 0.1)) m += 2;
		}
		return nodes;
	}
	
	public static void main (String[] args)
	{		
		double speed = 20000; // Tx and Rx speed, bytes per second
		
		// rxSpeed = Math.exp (rand.nextGaussian() + 11.74);
		// txSpeed = rxSpeed / 5.0;
		
		Network.reorder = true;
		Network.lossRate = 0.001;
		
		Node[] nodes = makeKleinbergNetwork (100, 4, speed);
		
		int key = Node.locationToKey (Math.random());
		Event.schedule (nodes[0], 0.0,
			Node.GENERATE_SSK_INSERT, key);
		Event.schedule (nodes[25], 30.0,
			Node.GENERATE_SSK_REQUEST, key);
		Event.schedule (nodes[50], 60.0,
			Node.GENERATE_SSK_COLLISION, key);
		Event.schedule (nodes[75], 90.0,
			Node.GENERATE_SSK_REQUEST, key);
		
		// Run the simulation
		Event.run();
	}
}
