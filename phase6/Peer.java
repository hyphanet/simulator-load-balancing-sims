import java.util.LinkedList;
import java.util.Iterator;
import java.util.HashSet;
import messages.Message;
import messages.Block;

class Peer
{
	private Node node; // The local node
	public int address; // The remote node's address
	public double location; // The remote node's routing location
	private double latency; // The latency of the connection in seconds
	
	// Retransmission parameters
	public final static double RTO = 4.0; // Retransmission timeout in RTTs
	public final static double FRTO = 1.5; // Fast retx timeout in RTTs
	public final static double RTT_DECAY = 0.9; // Exp moving average
	public final static double LINK_IDLE = 8.0; // RTTs without transmitting
	
	// Coalescing
	public final static double MAX_DELAY = 0.1; // Coalescing delay, seconds
	
	// Out-of-order delivery with eventual detection of missing packets
	public final static int SEQ_RANGE = 1000;
	
	// Sender state
	private double rtt = 5.0; // Estimated round-trip time in seconds
	private int txSeq = 0; // Sequence number of next outgoing data packet
	private int txMaxSeq = SEQ_RANGE - 1; // Highest sequence number
	private LinkedList<Packet> txBuffer; // Retransmission buffer
	private AckQueue ackQueue; // Outgoing acks
	private DeadlineQueue searchQueue; // Outgoing search messages
	private DeadlineQueue transferQueue; // Outgoing transfers
	private CongestionWindow window; // AIMD congestion window
	private double lastTransmission = 0.0; // Clock time
	
	// Receiver state
	private HashSet<Integer> rxDupe; // Detect duplicates by sequence number
	private int rxSeq = 0; // Sequence number of next in-order incoming pkt
	
	public Peer (Node node, int address, double location, double latency)
	{
		this.node = node;
		this.address = address;
		this.location = location;
		this.latency = latency;
		txBuffer = new LinkedList<Packet>();
		ackQueue = new AckQueue();
		searchQueue = new DeadlineQueue();
		transferQueue = new DeadlineQueue();
		window = new CongestionWindow (this);
		rxDupe = new HashSet<Integer>();
	}
	
	// Queue a message for transmission
	public void sendMessage (Message m)
	{
		if (m instanceof Block) {
			log (m + " added to transfer queue");
			transferQueue.add (m, Event.time() + MAX_DELAY);
		}
		else {
			log (m + " added to search queue");
			searchQueue.add (m, Event.time() + MAX_DELAY);
		}
		// Start the node's timer if necessary
		node.startTimer();
		// Send as many packets as possible
		while (send());
	}
	
	// Queue an ack for transmission
	private void sendAck (int seq)
	{
		log ("ack " + seq + " added to ack queue");
		ackQueue.add (seq, Event.time() + MAX_DELAY);
		// Start the node's timer if necessary
		node.startTimer();
		// Send as many packets as possible
		while (send());
	}
	
	// Try to send a packet, return true if a packet was sent
	private boolean send()
	{		
		if (ackQueue.size + searchQueue.size + transferQueue.size ==0) {
			log ("nothing to send");
			return false;
		}
		log (ackQueue.size + " bytes of acks in queue");
		log (searchQueue.size + " bytes of searches in queue");
		log (transferQueue.size + " bytes of transfers in queue");
		
		// Return to slow start when the link is idle
		double now = Event.time();
		if (now - lastTransmission > LINK_IDLE * rtt) window.reset();
		lastTransmission = now;
		
		// Delay small packets for coalescing
		if (now < deadline (now)) {
			int payload = searchQueue.size + transferQueue.size;
			log ("delaying transmission of " + payload + " bytes");
			return false;
		}
		
		Packet p = new Packet();
		
		// Put all waiting acks in the packet
		while (ackQueue.size > 0) p.addAck (ackQueue.pop());
		
		// Don't send sequence number n+SEQ_RANGE until sequence
		// number n has been acked - this limits the number of
		// sequence numbers the receiver must store for replay
		// detection. We must still be allowed to send acks,
		// otherwise the connection could deadlock.
		
		if (txSeq > txMaxSeq)
			log ("waiting for ack " + (txMaxSeq - SEQ_RANGE + 1));
		else if (window.available() <= 0)
			log ("no room in congestion window for messages");
		else if (node.bandwidth.available() <= 0)
			log ("no bandwidth available for messages");
		else pack (p); // OK to send data
		
		// If the packet contains data, buffer it for retransmission
		if (p.messages != null) {
			p.seq = txSeq++;
			p.sent = now;
			txBuffer.add (p);
			window.bytesSent (p.size);
		}
		
		// Send the packet
		log ("sending packet " + p.seq + ", " + p.size + " bytes");
		node.net.send (p, address, latency);
		node.bandwidth.remove (p.size);
		return true;
	}
	
	// Called by Node when a packet arrives
	public void handlePacket (Packet p)
	{
		if (p.messages != null) handleData (p);
		if (p.acks != null) for (int ack : p.acks) handleAck (ack);
	}
	
	private void handleData (Packet p)
	{
		log ("received packet " + p.seq + ", " + p.size + " bytes");
		if (p.seq < rxSeq || rxDupe.contains (p.seq)) {
			log ("duplicate packet");
			sendAck (p.seq); // Original ack may have been lost
		}
		else if (p.seq == rxSeq) {
			log ("packet in order");
			// Find the sequence number of the next missing packet
			int was = rxSeq;
			while (rxDupe.remove (++rxSeq));
			log ("rxSeq was " + was + ", now " + rxSeq);
			// Deliver the packet
			unpack (p);
			sendAck (p.seq);
		}
		else if (p.seq < rxSeq + SEQ_RANGE) {
			log ("packet out of order - expected " + rxSeq);
			if (rxDupe.add (p.seq)) unpack (p);
			else log ("duplicate packet");
			sendAck (p.seq); // Original ack may have been lost
		}
		// This indicates a misbehaving sender - discard the packet
		else log ("warning: received " + p.seq + " before " + rxSeq);
	}
	
	private void handleAck (int ack)
	{
		log ("received ack " + ack);
		double now = Event.time();
		Iterator<Packet> i = txBuffer.iterator();
		while (i.hasNext()) {
			Packet p = i.next();
			double age = now - p.sent;
			// Explicit ack
			if (p.seq == ack) {
				log ("packet " + p.seq + " acknowledged");
				i.remove();
				// Update the congestion window
				window.bytesAcked (p.size);
				// Update the average round-trip time
				rtt = rtt * RTT_DECAY + age * (1.0 - RTT_DECAY);
				log ("round-trip time " + age);
				log ("average round-trip time " + rtt);
				break;
			}
			// Fast retransmission
			if (p.seq < ack && age > FRTO * rtt + MAX_DELAY) {
				p.sent = now;
				log ("fast retransmitting packet " + p.seq);
				node.net.send (p, address, latency);
				window.fastRetransmission (now);
			}
		}
		// Recalculate the maximum sequence number
		if (txBuffer.isEmpty()) txMaxSeq = txSeq + SEQ_RANGE - 1;
		else txMaxSeq = txBuffer.peek().seq + SEQ_RANGE - 1;
		log ("maximum sequence number " + txMaxSeq);
		// Send as many packets as possible
		while (send());
	}
	
	// Add messages to a packet
	private void pack (Packet p)
	{
		// Add one search, then one transfer, then as many searches as
		// will fit. This ensures that neither searches nor transfers
		// starve as long as at least *some* searches are small enough
		// to share a packet with a transfer.
		
		if (searchQueue.size > 0
		&& p.size + searchQueue.headSize() <= Packet.MAX_SIZE)
			p.addMessage (searchQueue.pop());
		if (transferQueue.size > 0
		&& p.size + transferQueue.headSize() <= Packet.MAX_SIZE)
			p.addMessage (transferQueue.pop());
		while (searchQueue.size > 0
		&& p.size + searchQueue.headSize() <= Packet.MAX_SIZE)
			p.addMessage (searchQueue.pop());
	}
	
	// Remove messages from a packet and deliver them to the node
	private void unpack (Packet p)
	{
		if (p.messages == null) return;
		for (Message m : p.messages) node.handleMessage (m, this);
	}
	
	// Called by Node, returns the next coalescing or retx deadline
	public double checkTimeouts()
	{
		log ("checking timeouts");
		// Send as many packets as possible
		while (send());
		
		double now = Event.time();
		if (txBuffer.isEmpty()) {
			log ("no packets in flight");
			return deadline (now); // Sleep until the next deadline
		}
		for (Packet p : txBuffer) {
			if (now - p.sent > RTO * rtt + MAX_DELAY) {
				// Retransmission timeout
				log ("retransmitting packet " + p.seq);
				p.sent = now;
				node.net.send (p, address, latency);
				window.timeout (now);
			}
		}
		
		// Sleep for up to MAX_DELAY seconds until the next deadline
		return Math.min (now + MAX_DELAY, deadline (now));
	}
	
	// Work out when the first ack or search or transfer needs to be sent
	private double deadline (double now)
	{
		return Math.min (ackQueue.deadline(), dataDeadline (now));
	}
	
	// Work out when the first search or transfer needs to be sent
	private double dataDeadline (double now)
	{
		// If there's no data waiting, use the ack deadline
		if (searchQueue.size + transferQueue.size == 0)
			return Double.POSITIVE_INFINITY;
		
		double deadline = Math.min (searchQueue.deadline(),
						transferQueue.deadline());
		
		// Delay small packets until the coalescing deadline
		if (searchQueue.size + transferQueue.size
		< Packet.SENSIBLE_PAYLOAD)
			return deadline;
		
		// If there's not enough room in the window, wait for an ack
		if (window.available() <= 0)
			return Double.POSITIVE_INFINITY;
		
		// If there's not enough bandwidth, try again shortly
		if (node.bandwidth.available() <= 0)
			return Math.max (deadline, now + Node.SHORT_SLEEP);
		
		// Send a packet immediately
		return now;
	}
	
	public void log (String message)
	{
		Event.log (node.net.address + ":" + address + " " + message);
	}
}
