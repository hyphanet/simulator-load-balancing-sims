//
// Peer representation as seen from a SimpleNode
//
package sim;

class SimplePeer {
    
    static final int t_DARKNET=0;
    static final int t_OPENNET=1;

    SimpleNode n;        // The peer node
    SimpleLink l;        // Link to peer
    int t;

    /*
     * @target: the node which will become the peer
     * @link: the link to the peer
     * @type: type of peer (t.DARKNET or t_OPENNET)
     */
    public SimplePeer(SimpleNode target,SimpleLink link,int type) {
	n = target;
	l = link;	
	t = type;
    }

    public boolean drknetPeer() {
	return (t==t_DARKNET);
    }

    public boolean opennetPeer() {
	return (t==t_OPENNET);
    }
}

