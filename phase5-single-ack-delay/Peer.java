import java.util.LinkedList;
import java.util.Iterator;
import java.util.HashSet;

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
	
	// Coalescing
	public final static double MAX_DELAY = 0.1; // Coalescing delay, seconds
	
	// Out-of-order delivery with eventual detection of missing packets
	public final static int SEQ_RANGE = 1000;
	
	// Sender state
	private double rtt = 5.0; // Estimated round-trip time in seconds
	private int txSeq = 0; // Sequence number of next outgoing data packet
	private int txMaxSeq = SEQ_RANGE - 1; // Highest sequence number
	private LinkedList<Packet> txBuffer; // Retransmission buffer
	private LinkedList<Deadline<Message>> msgQueue; // Outgoing messages
	private int msgQueueSize = 0; // Size of message queue in bytes
	private LinkedList<Deadline<Integer>> ackQueue; // Outgoing acks
	private int ackQueueSize = 0; // Size of ack queue in bytes
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
		msgQueue = new LinkedList<Deadline<Message>>();
		ackQueue = new LinkedList<Deadline<Integer>>();
		window = new CongestionWindow (this);
		rxDupe = new HashSet<Integer>();
	}
	
	// Queue a message for transmission
	public void sendMessage (Message m)
	{
		log (m + " added to message queue");
		// Warning: until token-passing is implemented the length of
		// the message queue is unlimited
		double now = Event.time();
		msgQueue.add (new Deadline<Message> (m, now + MAX_DELAY));
		msgQueueSize += m.size;
		log (msgQueue.size() + " messages in queue");
		// Start the node's timer if necessary
		node.startTimer();
		// Send as many packets as possible
		while (send());
	}
	
	// Try to send a packet, return true if a packet was sent
	private boolean send()
	{		
		if (ackQueueSize == 0 && msgQueueSize == 0) {
			log ("no messages or acks to send");
			return false;
		}
		
		// Return to slow start when the link is idle
		double now = Event.time();
		if (now - lastTransmission > RTO * rtt) window.reset();
		lastTransmission = now;
		
		// Work out how large a packet we can send
		int headersAndAcks = Packet.HEADER_SIZE + ackQueueSize;
		int payload = Packet.MAX_SIZE - headersAndAcks;
		if (payload > msgQueueSize) payload = msgQueueSize;
		
		int win = window.available() - headersAndAcks;
		if (win <= 0) log ("no room in congestion window for messages");
		if (payload > win) payload = win;
		
		int bw = node.bandwidth.available() - headersAndAcks;
		if (bw <= 0) log ("no bandwidth available for messages");
		if (payload > bw) payload = bw;
		
		// Delay small packets for coalescing
		if (payload < Packet.SENSIBLE_PAYLOAD && now < deadline()) {
			log ("delaying transmission of " + payload + " bytes");
			return false;
		}
		
		Packet p = new Packet();
		
		// Put all waiting acks in the packet
		for (Deadline<Integer> a : ackQueue) {
			double delay = now - (a.deadline - MAX_DELAY);
			p.addAck (a.item, delay);
		}
		ackQueue.clear();
		ackQueueSize = 0;
		
		// Don't send sequence number n+SEQ_RANGE until sequence
		// number n has been acked - this limits the number of
		// sequence numbers the receiver must store for replay
		// detection. We must still be allowed to send acks,
		// otherwise the connection could deadlock.
		
		if (txSeq > txMaxSeq)
			log ("waiting for ack " + (txMaxSeq - SEQ_RANGE + 1));
		else {
			// Put as many messages as possible in the packet
			Iterator<Deadline<Message>> i = msgQueue.iterator();
			while (i.hasNext()) {
				Message m = i.next().item;
				if (m.size > payload) break;
				i.remove();
				msgQueueSize -= m.size;
				p.addMessage (m);
				payload -= m.size;
			}
		}
		
		// Don't send empty packets
		if (p.acks == null && p.messages == null) return false;
		
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
	
	private void sendAck (int seq)
	{
		log ("ack " + seq + " added to ack queue");
		double now = Event.time();
		ackQueue.add (new Deadline<Integer> (seq, now + MAX_DELAY));
		ackQueueSize += Packet.ACK_SIZE;
		log (ackQueue.size() + " acks in queue");
		// Start the node's timer if necessary
		node.startTimer();
		// Send as many packets as possible
		while (send());
	}
	
	// Called by Node when a packet arrives
	public void handlePacket (Packet p)
	{
		if (p.messages != null) handleData (p);
		if (p.acks != null) {
			updateRtt (p.acks.get (0), p.ackDelay);
			for (int a : p.acks) handleAck (a);
		}
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
	
	private void updateRtt (int ack, double delay)
	{
		for (Packet p : txBuffer) {
			if (p.seq == ack) {
				double r = Event.time() - p.sent - delay;
				rtt = rtt * RTT_DECAY + r * (1.0 - RTT_DECAY);
				log ("ack delay " + delay);
				log ("round-trip time " + r);
				log ("average round-trip time " + rtt);
				return;
			}
		}
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
				window.bytesAcked (p.size);
				break;
			}
			// Fast retransmission
			if (p.seq < ack && age > FRTO * rtt) {
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
		if (txBuffer.isEmpty()) {
			log ("no packets in flight");
			return deadline();
		}
		double now = Event.time();
		for (Packet p : txBuffer) {
			if (now - p.sent > RTO * rtt) {
				// Retransmission timeout
				log ("retransmitting packet " + p.seq);
				p.sent = now;
				node.net.send (p, address, latency);
				window.timeout (now);
			}
		}
		return Math.min (now + MAX_DELAY, deadline());
	}
	
	// Work out when the first ack or message needs to be sent
	private double deadline()
	{
		double deadline = Double.POSITIVE_INFINITY;
		Deadline<Integer> ack = ackQueue.peek();
		if (ack != null) deadline = ack.deadline;
		Deadline<Message> msg = msgQueue.peek();
		if (msg != null) deadline = Math.min (deadline, msg.deadline);
		return deadline;
	}
	
	public void log (String message)
	{
		Event.log (node.net.address + ":" + address + " " + message);
	}
}
