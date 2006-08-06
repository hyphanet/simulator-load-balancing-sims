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
	
	// Congestion control parameters
	public final static int MIN_CWIND = 3000; // Minimum congestion window
	public final static int MAX_CWIND = 100000; // Maximum congestion window
	public final static double ALPHA = 0.1615; // AIMD increase parameter
	public final static double BETA = 0.9375; // AIMD decrease parameter
	public final static double GAMMA = 3.0; // Slow start divisor
	
	// Coalescing parameters
	public final static double COALESCE_DATA = 0.1; // Max delay in seconds
	public final static double COALESCE_ACK = 0.1;
	
	// Out-of-order delivery with eventual detection of missing packets
	public final static int SEQ_RANGE = 1000; // Packets
	
	// Sender state
	private double cwind = MIN_CWIND; // Congestion window in bytes
	private boolean slowStart = true; // Are we in the slow start phase?
	private double rtt = 3.0; // Estimated round-trip time in seconds
	private double lastTransmission = 0.0; // Clock time
	private double lastLeftSlowStart = 0.0; // Clock time
	private int inflight = 0; // Bytes sent but not acked
	private int txSeq = 0; // Sequence number of next outgoing data packet
	private int txMaxSeq = SEQ_RANGE - 1; // Highest sequence number
	private LinkedList<Packet> txBuffer; // Retransmission buffer
	private LinkedList<Deadline<Message>> msgQueue; // Outgoing messages
	private int msgQueueSize = 0; // Size of message queue in bytes
	private LinkedList<Deadline<Integer>> ackQueue; // Outgoing acks
	private int ackQueueSize = 0; // Size of ack queue in bytes
	
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
		rxDupe = new HashSet<Integer>();
	}
	
	// Queue a message for transmission
	public void sendMessage (Message m)
	{
		log (m + " added to transmission queue");
		// Warning: until token-passing is implemented the length of
		// the transmission queue is unlimited
		double now = Event.time();
		msgQueue.add (new Deadline<Message> (m, now + COALESCE_DATA));
		msgQueueSize += m.size;
		log (msgQueue.size() + " messages in transmission queue");
		// Send as many packets as possible
		while (send());
	}
	
	// Try to send a packet, return true if a packet was sent
	private boolean send()
	{
		// Return to slow start when the link is idle
		double now = Event.time();
		if (now - lastTransmission > RTO * rtt) {
			log ("returning to slow start");
			cwind = MIN_CWIND;
			slowStart = true;
		}
		lastTransmission = now;
		
		if (ackQueueSize == 0 && msgQueueSize == 0) {
			log ("no messages or acks to send");
			return false;
		}
		
		Packet p = new Packet();
		
		int window = (int) cwind - inflight - p.size - ackQueueSize;
		if (window <= 0) log ("no room in congestion window");
		
		// Work out how large a packet we can send
		int payload = Packet.MAX_SIZE - p.size - ackQueueSize;
		if (payload > msgQueueSize) payload = msgQueueSize;
		if (payload > window) payload = window;
		
		// Work out when the first ack or message needs to be sent
		double deadline = Double.POSITIVE_INFINITY;
		Deadline<Integer> ack = ackQueue.peek();
		if (ack != null) deadline = ack.deadline;
		Deadline<Message> msg = msgQueue.peek();
		if (msg != null) deadline = Math.min (deadline, msg.deadline);
		
		// Delay small packets for coalescing
		if (payload < Packet.SENSIBLE_PAYLOAD && now < deadline) {
			log ("delaying transmission of " + payload + " bytes");
			return false;
		}
		
		// Put all waiting acks in the packet
		for (Deadline<Integer> a : ackQueue) p.addAck (a.item);
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
				if (p.size + m.size > Packet.MAX_SIZE) break;
				i.remove();
				msgQueueSize -= m.size;
				p.addMessage (m);
			}
		}
		
		// Don't send empty packets
		if (p.acks == null && p.messages == null) return false;
		
		// If the packet contains data, buffer it for retransmission
		if (p.messages != null) {
			p.seq = txSeq++;
			p.sent = now;
			inflight += p.size; // Acks aren't congestion-controlled
			log (inflight + " bytes in flight");
			txBuffer.add (p);
			// Start the node's retransmission timer if necessary
			node.startTimer();
		}
		
		// Send the packet
		log ("sending packet " + p.seq + ", " + p.size + " bytes");
		node.net.send (p, address, latency);
		return true;
	}
	
	private void sendAck (int seq)
	{
		double now = Event.time();
		ackQueue.add (new Deadline<Integer> (seq, now + COALESCE_ACK));
		ackQueueSize += Packet.ACK_SIZE;
		send();
	}
	
	// Called by Node when a packet arrives
	public void handlePacket (Packet p)
	{
		if (p.messages != null) handleData (p);
		if (p.acks != null) for (int seq : p.acks) handleAck (seq);
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
	
	private void handleAck (int seq)
	{
		log ("received ack " + seq);
		double now = Event.time();
		Iterator<Packet> i = txBuffer.iterator();
		while (i.hasNext()) {
			Packet p = i.next();
			double age = now - p.sent;
			// Explicit ack
			if (p.seq == seq) {
				log ("packet " + p.seq + " acknowledged");
				i.remove();
				inflight -= p.size;
				log (inflight + " bytes in flight");
				// Increase the congestion window
				if (slowStart) cwind += p.size / GAMMA;
				else cwind += p.size * p.size * ALPHA / cwind;
				if (cwind > MAX_CWIND) cwind = MAX_CWIND;
				log ("congestion window increased to " + cwind);
				// Update the average round-trip time
				rtt = rtt * RTT_DECAY + age * (1.0 - RTT_DECAY);
				log ("round-trip time " + age);
				log ("average round-trip time " + rtt);
				break;
			}
			// Fast retransmission
			if (p.seq < seq && age > FRTO * rtt) {
				p.sent = now;
				log ("fast retransmitting packet " + p.seq);
				log (inflight + " bytes in flight");
				node.net.send (p, address, latency);
				decreaseCongestionWindow (now);
			}
		}
		// Recalculate the maximum sequence number
		if (txBuffer.isEmpty()) txMaxSeq = txSeq + SEQ_RANGE - 1;
		else txMaxSeq = txBuffer.peek().seq + SEQ_RANGE - 1;
		log ("maximum sequence number " + txMaxSeq);
		// Send as many packets as possible
		while (send());
	}
	
	private void decreaseCongestionWindow (double now)
	{
		cwind *= BETA;
		if (cwind < MIN_CWIND) cwind = MIN_CWIND;
		log ("congestion window decreased to " + cwind);
		// The slow start phase ends when the first packet is lost
		if (slowStart) {
			log ("leaving slow start");
			slowStart = false;
			lastLeftSlowStart = now;
		}
	}
	
	// Remove messages from a packet and deliver them to the node
	private void unpack (Packet p)
	{
		if (p.messages == null) return;
		for (Message m : p.messages) node.handleMessage (m, this);
	}
	
	private void log (String message)
	{
		Event.log (node.net.address + ":" + address + " " + message);
	}
	
	// Called by Node
	public boolean checkTimeouts()
	{
		log ("checking timeouts");
		send(); // Consider sending delayed packets
		if (txBuffer.isEmpty()) {
			log ("no packets in flight");
			return false;
		}
		double now = Event.time();
		for (Packet p : txBuffer) {
			if (now - p.sent > RTO * rtt) {
				// Retransmission timeout
				p.sent = now;
				log ("retransmitting packet " + p.seq);
				log (inflight + " bytes in flight");
				node.net.send (p, address, latency);
				// Return to slow start
				if (!slowStart &&
				now - lastLeftSlowStart > RTO * rtt) {
					log ("returning to slow start");
					cwind = MIN_CWIND;
					slowStart = true;
				}
				else {
					log ("not returning to slow start");
					decreaseCongestionWindow (now);
				}
			}
		}
		return true;
	}
}
