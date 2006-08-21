// Interesting parameters to play with: txSpeed and rxSpeed, retransmission
// timeout, window sizes, AIMD increase and decrease (Peer.java), queue sizes
// (NetworkInterface.java), packet size (Packet.java).

class Sim
{
	public static void main (String[] args)
	{		
		double txSpeed = 15000, rxSpeed = 15000; // Bytes per second
		// rxSpeed = Math.exp (rand.nextGaussian() + 11.74);
		// txSpeed = rxSpeed / 5.0;
		
		Network.reorder = true;
		Network.lossRate = 0.001;
		
		Node n0 = new Node (txSpeed, rxSpeed);
		Node n1 = new Node (txSpeed, rxSpeed);
		Node n2 = new Node (txSpeed, rxSpeed);
		Node n3 = new Node (txSpeed, rxSpeed);
		
		n0.connectBothWays (n1, 0.001);
		n1.connectBothWays (n2, 0.001);
		n1.connectBothWays (n3, 0.001);
		n2.connectBothWays (n3, 0.001);
		
		for (int i = 0; i < 5; i++) {
			int key = Node.locationToKey (Math.random());
			n3.cache.put (key);
			Event.schedule (n0, 0.0, Node.GENERATE_REQUEST, key);
		}
		
		// Run the simulation
		Event.run();
	}
}
