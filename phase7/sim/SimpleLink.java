package sim;

import java.util.LinkedList;
import java.util.Random;

class SimpleLink {

    double d;
    SimpleNode a,b;
    static boolean use_delays=false;
    static final Random rand = new Random(System.currentTimeMillis() % 10000);
    // fixme: event queue for delivery of messages    

    public void use_delays() {
	use_delays = true;
    }

    public SimpleLink(SimpleNode aend,SimpleNode bend) {
	this(aend,bend,SimpleGraph.sampleDelay());
    }

    public SimpleLink(SimpleNode aend,SimpleNode bend,double delay) {
	a = aend;
	b = bend;
	d = delay;
    }

    // Assumption: the application will know how to handle a link if its directed (fixme?)
    public LinkedList<SimpleQuery> send(SimpleQuery q) {
	
	if (q.source==a) {
	    return b.recv(q);
	} else if (q.source==b) {
	    return a.recv(q);
	} else {
	    System.err.println("SimpleLink.send(): Simulation error, unknown target?");
	    System.exit(-1);
	}
	return null;
    }

}
