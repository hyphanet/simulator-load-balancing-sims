package sim;
import sim.messages.*;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.HashSet;

public class Peer implements EventTarget
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
	private final static double MAX_SLEEP = 0.1; // Max coalescing delay
	private final static double MIN_SLEEP = 0.01; // Forty winks
	
	// Out-of-order delivery with duplicate detection
	public final static int SEQ_RANGE = 1000;
	
	// Sender state
	private double rtt = 5.0; // Estimated round-trip time in seconds
	private int txSeq = 0; // Sequence number of next outgoing data packet
	private int txMaxSeq = SEQ_RANGE - 1; // Highest sequence number
	private LinkedList<Packet> txBuffer; // Retransmission buffer
	private DeadlineQueue<Ack> ackQueue; // Outgoing acks
	private DeadlineQueue<Message> searchQueue; // Outgoing search messages
	private DeadlineQueue<Message> transferQueue; // Outgoing transfers
	private CongestionWindow window; // AIMD congestion window
	private double lastTransmission = Double.POSITIVE_INFINITY; // Time
	private boolean tgif = false; // "Transfers go in first" toggle
	private boolean timerRunning = false; // Coalescing timer
	private double pollingInterval; // Poll the bandwidth limiter
	
	// Receiver state
	private HashSet<Integer> rxDupe; // Detect duplicates by sequence number
	private int rxSeq = 0; // Sequence number of next in-order incoming pkt
	
	// Flow control
	public int tokensOut = 0; // How many requests/inserts can we send?
	public int tokensIn = 0; // How many requests/inserts should we accept?
	
	public Peer (Node node, int address, double location, double latency)
	{
		this.node = node;
		this.address = address;
		this.location = location;
		this.latency = latency;
		txBuffer = new LinkedList<Packet>();
		ackQueue = new DeadlineQueue<Ack>();
		searchQueue = new DeadlineQueue<Message>();
		transferQueue = new DeadlineQueue<Message>();
		window = new CongestionWindow (this);
		rxDupe = new HashSet<Integer>();
		// Poll the bandwidth limiter at reasonable intervals
		pollingInterval = Packet.SENSIBLE_PAYLOAD / node.bandwidth.rate;
		if (pollingInterval > MAX_SLEEP) pollingInterval = MAX_SLEEP;
		if (pollingInterval < MIN_SLEEP) pollingInterval = MIN_SLEEP;
	}
	
	// Queue a message for transmission
	public void sendMessage (Message m)
	{
		m.deadline = Event.time() + MAX_SLEEP;
		if (m instanceof Block) {
			log (m + " added to transfer queue");
			transferQueue.add (m);
		}
		else {
			log (m + " added to search queue");
			searchQueue.add (m);
		}
		// Start the coalescing timer
		startTimer();
		// Send as many packets as possible
		while (send());
	}
	
	// Queue an ack for transmission
	private void sendAck (int seq)
	{
		log ("ack " + seq + " added to ack queue");
		ackQueue.add (new Ack (seq, Event.time() + MAX_SLEEP));
		// Start the coalescing timer
		startTimer();
		// Send as many packets as possible
		while (send());
	}
	
	// Start the coalescing timer
	private void startTimer()
	{
		if (timerRunning) return;
		timerRunning = true;
		log ("starting coalescing timer");
		Event.schedule (this, MAX_SLEEP, CHECK_DEADLINES, null);
	}
	
	// Try to send a packet, return true if a packet was sent
	private boolean send()
	{
		int waiting = ackQueue.size+searchQueue.size+transferQueue.size;
		log (waiting + " bytes waiting");
		if (waiting == 0) return false;
		
		// Return to slow start when the link is idle
		double now = Event.time();
		if (now - lastTransmission > LINK_IDLE * rtt) window.reset();
		lastTransmission = now;
		
		// How many bytes can we send?
		int size = Math.min (Packet.MAX_SIZE, window.available());
		size = Math.min (size, node.bandwidth.available());
		log (size + " bytes available for packet");
		
		// Urgent acks to send?
		if (ackQueue.deadline() <= now) return sendPacket (size);
		// Urgent searches and room to send them?
		if (searchQueue.deadline() <= now
		&& searchQueue.headSize() <= size) return sendPacket (size);
		// Urgent transfers and room to send them?
		if (transferQueue.deadline() <= now
		&& transferQueue.headSize() <= size) return sendPacket (size);
		// Enough non-urgent messages for a large packet?
		if (waiting >= Packet.SENSIBLE_PAYLOAD
		&& size >= Packet.SENSIBLE_PAYLOAD) return sendPacket (size);
		
		log ("not sending a packet");
		return false;
	}
	
	private boolean sendPacket (int maxSize)
	{
		// Construct a packet
		Packet p = new Packet();
		while (ackQueue.size > 0) p.addAck (ackQueue.pop());
		log ((maxSize - p.size) + " bytes available for messages");
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
			if (p.messages == null) log ("no messages added");
			else p.seq = txSeq++;
		}
		else log ("waiting for ack " + (txMaxSeq - SEQ_RANGE + 1));
		// Don't send empty packets
		if (p.acks == null && p.messages == null) return false;
		// Transmit the packet
		log ("sending packet " + p.seq + ", " + p.size + " bytes");
		node.net.send (p, address, latency);
		node.bandwidth.remove (p.size);
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
		if (p.acks != null) for (Ack a : p.acks) handleAck (a);
		if (p.messages != null) handleData (p);
	}
	
	private void handleData (Packet p)
	{
		log ("received packet " + p.seq + ", expected " + rxSeq);
		if (p.seq < rxSeq || rxDupe.contains (p.seq)) {
			log ("duplicate packet");
			sendAck (p.seq); // Original ack may have been lost
		}
		else if (p.seq == rxSeq) {
			// Find the sequence number of the next missing packet
			while (rxDupe.remove (++rxSeq));
			log ("packet in order, now expecting " + rxSeq);
			// Deliver the messages to the node
			for (Message m : p.messages)
				node.handleMessage (m, this);
			sendAck (p.seq);
		}
		else if (p.seq < rxSeq + SEQ_RANGE) {
			log ("packet out of order");
			rxDupe.add (p.seq);
			// Deliver the messages to the node
			for (Message m : p.messages)
				node.handleMessage (m, this);
			sendAck (p.seq);
		}
		// This indicates a misbehaving sender - discard the packet
		else log ("WARNING: sequence number out of range");
	}
	
	private void handleAck (Ack a)
	{
		int seq = a.id;
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
				// Update the congestion window
				window.bytesAcked (p.size);
				// Update the average round-trip time
				rtt = rtt * RTT_DECAY + age * (1.0 - RTT_DECAY);
				log ("round-trip time " + age);
				log ("average round-trip time " + rtt);
				break;
			}
			// Fast retransmission
			if (p.seq < seq && age > FRTO * rtt + MAX_SLEEP) {
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
		// Send as many packets a possible
		if (timerRunning) while (send());
		else checkDeadlines();
	}
	
	// Check retx timeouts, return true if there are packets in flight
	public boolean checkTimeouts()
	{
		log (txBuffer.size() + " packets in flight");
		if (txBuffer.isEmpty()) return false;
		
		double now = Event.time();
		for (Packet p : txBuffer) {
			if (now - p.sent > RTO * rtt + MAX_SLEEP) {
				// Retransmission timeout
				log ("retransmitting packet " + p.seq);
				p.sent = now;
				node.net.send (p, address, latency);
				window.timeout (now);
			}
		}
		return true;
	}
	
	// Event callback: wake up, send packets, go back to sleep
	private void checkDeadlines()
	{
		// Send as many packets as possible
		while (send());
		// Find the next coalescing deadline - ignore message deadlines
		// if there isn't room in the congestion window to send them
		double dl = ackQueue.deadline();
		int win = window.available() -Packet.HEADER_SIZE -ackQueue.size;
		if (searchQueue.headSize() <= win)
			dl = Math.min (dl, searchQueue.deadline());
		if (transferQueue.headSize() <= win)
			dl = Math.min (dl, transferQueue.deadline());
		// If there's no deadline, stop the timer
		if (dl == Double.POSITIVE_INFINITY) {
			if (timerRunning) {
				log ("stopping coalescing timer");
				timerRunning = false;
			}
			return;
		}
		// Schedule the next check
		double sleep = dl - Event.time();
		if (shouldPoll()) sleep = Math.max (sleep, pollingInterval);
		else sleep = Math.max (sleep, MIN_SLEEP);
		timerRunning = true;
		log ("sleeping for " + sleep + " seconds");
		Event.schedule (this, sleep, CHECK_DEADLINES, null);
	}
	
	// Are we waiting for the bandwidth limiter?
	private boolean shouldPoll()
	{
		double now = Event.time();
		if (ackQueue.deadline() < now + pollingInterval) return false;
		
		double bw = node.bandwidth.available();
		double win = window.available();
		
		if (searchQueue.headSize() > bw
		&& searchQueue.headSize() <= win
		&& searchQueue.deadline() <= now) return true;
		
		if (transferQueue.headSize() > bw
		&& transferQueue.headSize() <= win
		&& transferQueue.deadline() <= now) return true;
		
		return false;
	}
	
	public void log (String message)
	{
		// Event.log (node.net.address + ":" + address + " " + message);
	}
	
	public String toString()
	{
		return Integer.toString (address);
	}
	
	// EventTarget interface
	public void handleEvent (int type, Object data)
	{
		if (type == CHECK_DEADLINES) checkDeadlines();
	}
	
	private final static int CHECK_DEADLINES = 1;
}
