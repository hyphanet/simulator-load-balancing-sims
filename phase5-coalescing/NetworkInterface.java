import java.util.LinkedList;

class NetworkInterface implements EventTarget
{
	public int address; // Represents an IP address and port
	private Node node; // The owner of this network interface
	private double txSpeed, rxSpeed; // Bytes per second
	
	private LinkedList<Packet> txQueue; // Queue of outgoing packets
	private LinkedList<Packet> rxQueue; // Queue of incoming packets
	private int txQueueSize, rxQueueSize; // Limited-size drop-tail queues
	private int txQueueMaxSize, rxQueueMaxSize; // Bytes
	
	public NetworkInterface (Node node, double txSpeed, double rxSpeed)
	{
		this.node = node;
		this.txSpeed = txSpeed;
		this.rxSpeed = rxSpeed;
		txQueue = new LinkedList<Packet>();
		rxQueue = new LinkedList<Packet>();
		txQueueSize = rxQueueSize = 0; // Bytes currently queued
		txQueueMaxSize = 10000;
		rxQueueMaxSize = 20000;
		// Attach the interface to the network
		address = Network.register (this);
	}
		
	// Called by Peer
	public void send (Packet p, int dest, double latency)
	{
		p.src = address;
		p.dest = dest;
		p.latency = latency;
		if (txQueueSize + p.size > txQueueMaxSize) {
			log ("no room in txQueue, " + p + " lost");
			return;
		}
		txQueue.add (p);
		txQueueSize += p.size;
		log (txQueueSize + " bytes in txQueue");
		// If there are no other packets in the queue, start to transmit
		if (txQueue.size() == 1) txStart (p);
	}
	
	// Event callbacks
	
	// Add a packet to the rx queue
	private void rxQueueAdd (Packet p)
	{
		if (rxQueueSize + p.size > rxQueueMaxSize) {
			log ("no room in rxQueue, " + p + " lost");
			return;
		}
		rxQueue.add (p);
		rxQueueSize += p.size;
		log (rxQueueSize + " bytes in rxQueue");
		// If there are no other packets in the queue, start to receive
		if (rxQueue.size() == 1) rxStart (p);
	}
	
	// Start receiving a packet
	private void rxStart (Packet p)
	{
		log ("starting to receive " + p);
		// Delay depends on rx speed
		Event.schedule (this, p.size / rxSpeed, RX_END, p);
	}
	
	// Finish receiving a packet, pass it to the node
	private void rxEnd (Packet p)
	{
		log ("finished receiving " + p);
		node.handlePacket (p);
		rxQueueSize -= p.size;
		rxQueue.remove (p);
		// If there's another packet waiting, start to receive it
		if (!rxQueue.isEmpty()) rxStart (rxQueue.peek());
	}
	
	// Start transmitting a packet
	private void txStart (Packet p)
	{
		log ("starting to transmit " + p);
		// Delay depends on tx speed
		Event.schedule (this, p.size / txSpeed, TX_END, p);
	}
	
	// Finish transmitting a packet
	private void txEnd (Packet p)
	{
		log ("finished transmitting " + p);
		Network.deliver (p);
		txQueueSize -= p.size;
		txQueue.remove (p);
		// If there's another packet waiting, start to transmit it
		if (!txQueue.isEmpty()) txStart (txQueue.peek());
	}
	
	private void log (String message)
	{
		// Event.log (address + " " + message);
	}
	
	// EventTarget interface
	public void handleEvent (int type, Object data)
	{
		switch (type) {
			case RX_QUEUE:
			rxQueueAdd ((Packet) data);
			break;
			
			case RX_END:
			rxEnd ((Packet) data);
			break;
			
			case TX_END:
			txEnd ((Packet) data);
			break;
		}
	}
	
	// Each EventTarget class has its own event codes
	public final static int RX_QUEUE = 1;
	private final static int RX_END = 2;
	private final static int TX_END = 3;
}
