// Interesting parameters to play with: txSpeed and rxSpeed, retransmission
// timeout, window sizes, AIMD increase and decrease (Peer.java), queue sizes
// (NetworkInterface.java), packet size (Packet.java).

class Sim
{
	public static void main (String[] args)
	{		
		double txSpeed = 20000, rxSpeed = 20000; // Bytes per second
		// rxSpeed = Math.exp (rand.nextGaussian() + 11.74);
		// txSpeed = rxSpeed / 5.0;
		
		Network.reorder = true;
		Network.lossRate = 0.001;
		
		Node[] nodes = new Node[20];
		for (int i = 0; i < 20; i++)
			nodes[i] = new Node (0.05 * i, txSpeed, rxSpeed);
		for (int i = 0; i < 20; i++) {
			nodes[i].connectBothWays (nodes[(i+1)%20], 0.1);
			nodes[i].connectBothWays (nodes[(i+2)%20], 0.1);
		}
		
		int key = Node.locationToKey (Math.random());
		Event.schedule (nodes[0], 0.0,
			Node.GENERATE_SSK_INSERT, key);
		Event.schedule (nodes[5], 30.0,
			Node.GENERATE_SSK_REQUEST, key);
		Event.schedule (nodes[10], 60.0,
			Node.GENERATE_SSK_COLLISION, key);
		Event.schedule (nodes[15], 90.0,
			Node.GENERATE_SSK_REQUEST, key);
		
		// Run the simulation
		Event.run();
	}
}
