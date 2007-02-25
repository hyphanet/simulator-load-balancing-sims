package sim;
import sim.messages.*;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.HashSet;

public class Peer
{
	public final static boolean LOG = false;
	
	private Node node; // The local node
	public int address; // The remote node's address
	public double location; // The remote node's routing location
	private double latency; // The latency of the connection in seconds
	
	// Retransmission parameters
	public final static double RTO = 4.0; // Retransmission timeout in RTTs
	public final static double FRTO = 1.5; // Fast retx timeout in RTTs
	public final static double RTT_DECAY = 0.9; // Exp moving average
	public final static double LINK_IDLE = 8.0; // RTTs without transmitting
	public final static double MAX_DELAY = 0.1; // Coalescing delay, seconds
	
	// Backoff
	public final static double INITIAL_BACKOFF = 1.0; // Seconds
	public final static double BACKOFF_MULTIPLIER = 2.0;
	public final static double MAX_BACKOFF = 10800.0; // Three hours!?
	
	// Out-of-order delivery with duplicate detection
	public final static int SEQ_RANGE = 65536;
	
	// Sender state
	private double rtt = 5.0; // Estimated round-trip time in seconds
	private int txSeq = 0; // Sequence number of next outgoing data packet
	private int txMaxSeq = SEQ_RANGE - 1; // Highest sequence number
	private LinkedList<Packet> txBuffer; // Retransmission buffer
	private DeadlineQueue<Message> searchQueue; // Outgoing search messages
	private DeadlineQueue<Message> transferQueue; // Outgoing transfers
	private CongestionWindow window; // AIMD congestion window
	private double lastTransmission = Double.POSITIVE_INFINITY; // Abs. time
	private boolean tgif = false; // "Transfers go in first" toggle
	
	// Receiver state
	private HashSet<Integer> rxDupe; // Detect duplicates by sequence number
	private int rxSeq = 0; // Sequence number of next in-order incoming pkt
	
	// Flow control
	private int tokensOut = 0; // How many searches can we send?
	private int tokensIn = 0; // How many searches should we accept?
	public double backoffUntil = 0.0; // Absolute time, seconds
	public double backoffLength = INITIAL_BACKOFF; // Relative time, seconds
	
	public Peer (Node node, int address, double location, double latency)
	{
		this.node = node;
		this.address = address;
		this.location = location;
		this.latency = latency;
		txBuffer = new LinkedList<Packet>();
		searchQueue = new DeadlineQueue<Message>();
		transferQueue = new DeadlineQueue<Message>();
		window = new CongestionWindow (this);
		rxDupe = new HashSet<Integer>();
	}
	
	// Queue a message for transmission
	public void sendMessage (Message m)
	{
		m.deadline = Event.time() + MAX_DELAY;
		if (m instanceof Block) {
			if (LOG) log (m + " added to transfer queue");
			transferQueue.add (m);
		}
		else {
			if (LOG) log (m + " added to search queue");
			searchQueue.add (m);
		}
		// Start the coalescing timer
		node.startTimer();
		// Send as many packets as possible
		while (send (-1));
	}
	
	// Try to send a packet, return true if a packet was sent
	private boolean send (int ack)
	{
		int waiting = searchQueue.size + transferQueue.size;
		if (LOG) log (waiting + " bytes waiting");
		if (ack == -1 && waiting == 0) return false;
		
		// Return to slow start when the link is idle
		double now = Event.time();
		if (now - lastTransmission > LINK_IDLE * rtt) window.reset();
		lastTransmission = now;
		
		// How many bytes can we send?
		int size = Math.min (Packet.MAX_SIZE, window.available());
		size = Math.min (size, node.bandwidth.available());
		if (LOG) log (size + " bytes available for packet");
		
		// Ack to send?
		if (ack != -1) return sendPacket (ack, size);
		// Urgent searches and room to send them?
		if (searchQueue.deadline() <= now
		&& searchQueue.headSize() <= size)
			return sendPacket (ack, size);
		// Urgent transfers and room to send them?
		if (transferQueue.deadline() <= now
		&& transferQueue.headSize() <= size)
			return sendPacket (ack, size);
		// Enough non-urgent messages for a large packet, and room?
		if (waiting >= Packet.SENSIBLE_PAYLOAD
		&& size >= Packet.SENSIBLE_PAYLOAD)
			return sendPacket (ack, size);
		
		if (LOG) log ("not sending a packet");
		return false;
	}
	
	// Try to send a packet up to the specified size, return true if sent
	private boolean sendPacket (int ack, int maxSize)
	{
		// Construct a packet
		Packet p = new Packet (node.net.address, address, latency, ack);
		if (LOG) log ((maxSize - p.size) + " bytes for messages");
		// Don't allow more than SEQ_RANGE payloads to be in flight
		if (txSeq <= txMaxSeq) {
			// Alternate priority between searches and transfers
			if (tgif) {
				p.addMessages (transferQueue, maxSize);
				p.addMessages (searchQueue, maxSize);
				tgif = false;
			}
			else {
				p.addMessages (searchQueue, maxSize);
				p.addMessages (transferQueue, maxSize);
				tgif = true;
			}
			if (p.messages == null) {
				if (LOG) log ("no messages added");
			}
			else p.seq = txSeq++;
		}
		else if (LOG) {
			log ("waiting for ack " + (txMaxSeq - SEQ_RANGE + 1));
		}
		// Don't send empty packets
		if (p.ack == -1 && p.messages == null) return false;
		// Transmit the packet
		if (LOG) log ("sending packet " +p.seq+ ", " +p.size+ " bytes");
		node.sendPacket (p);
		// If the packet contains data, buffer it for retransmission
		if (p.messages != null) {
			p.sent = Event.time();
			txBuffer.add (p);
			node.startTimer(); // Start the retransmission timer
			window.bytesSent (p.size);
		}
		return true;
	}
	
	// Called by Node when a packet arrives
	public void handlePacket (Packet p)
	{
		if (p.ack != -1) handleAck (p.ack);
		if (p.messages != null) handleData (p);
	}
	
	private void handleData (Packet p)
	{
		if (LOG) log ("received packet " +p.seq+ ", expected " +rxSeq);
		if (p.seq < rxSeq || rxDupe.contains (p.seq)) {
			if (LOG) log ("duplicate packet");
			send (p.seq); // Original ack may have been lost
		}
		else if (p.seq == rxSeq) {
			// Find the sequence number of the next missing packet
			while (rxDupe.remove (++rxSeq));
			if (LOG) log ("packet in order, now expecting " +rxSeq);
			// Deliver the messages to the node
			for (Message m : p.messages)
				node.handleMessage (m, this);
			send (p.seq);
		}
		else if (p.seq < rxSeq + SEQ_RANGE) {
			if (LOG) log ("packet out of order");
			rxDupe.add (p.seq);
			// Deliver the messages to the node
			for (Message m : p.messages)
				node.handleMessage (m, this);
			send (p.seq);
		}
		// This indicates a misbehaving sender - discard the packet
		else if (LOG) log ("WARNING: sequence number out of range");
	}
	
	private void handleAck (int ack)
	{
		if (LOG) log ("received ack " + ack);
		double now = Event.time();
		Iterator<Packet> i = txBuffer.iterator();
		while (i.hasNext()) {
			Packet p = i.next();
			double age = now - p.sent;
			// Explicit ack
			if (p.seq == ack) {
				i.remove();
				// Update the congestion window
				window.bytesAcked (p.size);
				// Update the average round-trip time
				rtt = rtt * RTT_DECAY + age * (1.0 - RTT_DECAY);
				if (LOG) {
					log ("packet " + ack + " acknowledged");
					log ("round-trip time " + age);
					log ("average round-trip time " + rtt);
				}
				break;
			}
			// Fast retransmission
			if (p.seq < ack && age > FRTO * rtt) {
				p.sent = now;
				if (LOG) log ("fast retransmitting " + p.seq);
				node.resendPacket (p);
				window.fastRetransmission (now);
			}
		}
		// Recalculate the maximum sequence number
		if (txBuffer.isEmpty()) txMaxSeq = txSeq + SEQ_RANGE - 1;
		else txMaxSeq = txBuffer.peek().seq + SEQ_RANGE - 1;
		if (LOG) log ("maximum sequence number " + txMaxSeq);
		// Send as many packets as possible
		while (send (-1));
	}
	
	// When a local RejectedOverload is received, back off unless backed off
	public void localRejectedOverload()
	{
		if (!Node.useBackoff) return;
		double now = Event.time();
		if (now < backoffUntil) return; // Already backed off
		backoffLength *= BACKOFF_MULTIPLIER;
		if (backoffLength > MAX_BACKOFF) backoffLength = MAX_BACKOFF;
		backoffUntil = now + backoffLength * Math.random();
		if (LOG) log ("backing off until " + backoffUntil);
	}
	
	// When a search is accepted, reset the backoff length unless backed off
	public void successNotOverload()
	{
		if (!Node.useBackoff) return;
		if (Event.time() < backoffUntil) return;
		backoffLength = INITIAL_BACKOFF;
		if (LOG) log ("resetting backoff length");
	}
	
	// Add outgoing tokens
	public void addTokensOut (int tokens)
	{
		tokensOut += tokens;
		if (tokensOut > 0) node.addAvailablePeer (this);
	}
	
	// Remove outgoing tokens
	public void removeTokensOut (int tokens)
	{
		tokensOut -= tokens;
		if (tokensOut <= 0) node.removeAvailablePeer (this);
	}
	
	// Return the number of outgoing tokens
	public int getTokensOut()
	{
		return tokensOut;
	}
	
	// Add incoming tokens
	public void addTokensIn (int tokens)
	{
		tokensIn += tokens;
		sendMessage (new Token (tokens)); // Inform the other side
	}
	
	// Remove incoming tokens
	public void removeTokensIn (int tokens)
	{
		tokensIn -= tokens;
	}
	
	// Return the number of incoming tokens
	public int getTokensIn()
	{
		return tokensIn;
	}
	
	// Called by Node - return true if there are messages outstanding
	public boolean timer()
	{
		// Stop the timer if there's nothing to wait for
		if (searchQueue.size + transferQueue.size == 0
		&& txBuffer.isEmpty()) return false;
		// Send as many packets as possible
		while (send (-1));
		// Check the retransmission timeouts
		double now = Event.time();
		for (Packet p : txBuffer) {
			if (now - p.sent > RTO * rtt) {
				// Retransmission timeout
				if (LOG) log ("retransmitting " + p.seq);
				p.sent = now;
				node.resendPacket (p);
				window.timeout (now);
			}
		}
		return true;
	}
	
	public void log (String message)
	{
		Event.log (node.net.address + ":" + address + " " + message);
	}
	
	public String toString()
	{
		return Integer.toString (address);
	}
}
