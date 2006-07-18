import java.util.HashMap;

class Node implements EventTarget
{
	public NetworkInterface net;
	private HashMap<Integer,Peer> peers; // Look up a peer by its address
	private int messagesSent = 0;
	
	public Node (double txSpeed, double rxSpeed)
	{
		peers = new HashMap<Integer,Peer>();
		net = new NetworkInterface (this, txSpeed, rxSpeed);
	}
	
	public void connect (Node n, double latency)
	{
		Peer p = new Peer (n.net.address, latency, this);
		peers.put (n.net.address, p);
	}
	
	// Called by NetworkInterface
	public void handlePacket (Packet packet)
	{
		Peer peer = peers.get (packet.src);
		if (peer == null) log ("unknown peer!");
		else peer.handlePacket (packet);
	}
	
	// Called by Peer
	public void messagesWaiting (Peer p)
	{
		for (Message m = p.receiveMessage(); m != null; m = p.receiveMessage())
			log ("received message " + m.seq + ", " + m.size + " bytes");
	}
	
	private void log (String message)
	{
		Event.log (net.address + " " + message);
	}
	
	// Event callback
	private void sendMessages()
	{
		// Send a message to each peer
		for (Peer p : peers.values()) {
			int size = (int) (Math.random() * 2500);
			Message m = new Message (messagesSent, size);
			log ("sending message " + m.seq + ", " + m.size + " bytes");
			p.sendMessage (m);
		}
		// Send a total of 1000 messages to each peer
		messagesSent++;
		if (messagesSent < 1000)
			Event.schedule (this, 0.1, SEND_MESSAGES, null);
	}
	
	// EventTarget interface
	public void handleEvent (int type, Object data)
	{
		if (type == SEND_MESSAGES) sendMessages();
	}
	
	// Each EventTarget class has its own event codes
	public final static int SEND_MESSAGES = 1;
}
